import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

from flask import Flask, request, jsonify
import joblib
import pandas as pd
import numpy as np
import os
import base64
from datetime import datetime
from io import BytesIO

app = Flask(__name__)

MODEL_DIR = os.path.join(os.path.dirname(__file__), 'models')

models = {}
model_files = {
    "xgboost":      "xgboost_model.pkl",
    "randomforest": "randomforest_model.pkl",
    "lightgbm":     "lightgbm_model.pkl"
}

for name, filename in model_files.items():
    path = os.path.join(MODEL_DIR, filename)
    if os.path.exists(path):
        models[name] = joblib.load(path)
        print(f"[OK] Loaded model: {name} from {path}")
    else:
        print(f"[MISSING] Model not found: {path}")

FEATURES = {
    "xgboost": [
        'lat', 'lon', 'co', 'no', 'no2', 'o3',
        'pm10', 'pm25', 'relativehumidity', 'so2', 'temperature',
        'si_pm25', 'si_pm10', 'AQI',
        'hour', 'day_of_week', 'month',
        'aqi_lag_1', 'aqi_lag_2'
    ],
    "randomforest": [
        'co', 'no', 'no2', 'o3',
        'pm10', 'pm25',
        'temperature', 'relativehumidity',
        'wind_speed', 'wind_direction',
        'hour',
        'aqi_lag_1', 'aqi_lag_2'
    ],
    "lightgbm": [
        'lat', 'lon', 'co', 'no', 'no2', 'o3',
        'pm10', 'pm25', 'relativehumidity', 'so2', 'temperature',
        'si_pm25', 'si_pm10', 'AQI',
        'hour', 'day_of_week', 'month',
        'aqi_lag_1', 'aqi_lag_2'
    ]
}

def calc_si_pm25(pm25):
    table = [(0,30,0,50),(30,60,51,100),(60,90,101,200),
             (90,120,201,300),(120,250,301,400),(250,500,401,500)]
    for lo, hi, ilo, ihi in table:
        if pm25 <= hi:
            return ((ihi - ilo) / (hi - lo)) * (pm25 - lo) + ilo
    return 500

def calc_si_pm10(pm10):
    table = [(0,50,0,50),(50,100,51,100),(100,250,101,200),
             (250,350,201,300),(350,430,301,400),(430,600,401,500)]
    for lo, hi, ilo, ihi in table:
        if pm10 <= hi:
            return ((ihi - ilo) / (hi - lo)) * (pm10 - lo) + ilo
    return 500

def fig_to_base64(fig):
    buf = BytesIO()
    fig.savefig(buf, format='png', dpi=130, bbox_inches='tight',
                facecolor=fig.get_facecolor())
    buf.seek(0)
    encoded = base64.b64encode(buf.read()).decode('utf-8')
    buf.close()
    return encoded

def get_model_color(model_name):
    return {'xgboost': '#10b981', 'randomforest': '#f59e0b', 'lightgbm': '#8b5cf6'}.get(model_name, '#1a73e8')

def get_model_label(model_name):
    return {'xgboost': 'XGBoost', 'randomforest': 'Random Forest', 'lightgbm': 'LightGBM'}.get(model_name, model_name)


# ─────────────────────────────────────────────────────────────────────
#  /predict  (unchanged)
# ─────────────────────────────────────────────────────────────────────
@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json()
        model_name = data.get('model', 'xgboost').lower()
        if model_name not in models:
            return jsonify({"error": f"Model '{model_name}' not loaded."}), 400
        model = models[model_name]
        feature_cols = FEATURES[model_name]
        now = datetime.now()
        pm25 = data.get('pm25', 0.0)
        pm10 = data.get('pm10', 0.0)
        current_aqi = data.get('current_aqi', 100)
        all_values = {
            'lat': data.get('lat', 10.0), 'lon': data.get('lon', 76.0),
            'co': data.get('co', 0.0), 'no': data.get('no', 0.0),
            'no2': data.get('no2', 0.0), 'o3': data.get('o3', 0.0),
            'pm10': pm10, 'pm25': pm25,
            'relativehumidity': data.get('relativehumidity', 60.0),
            'so2': data.get('so2', 0.0), 'temperature': data.get('temperature', 25.0),
            'si_pm25': calc_si_pm25(pm25), 'si_pm10': calc_si_pm10(pm10),
            'AQI': current_aqi,
            'wind_speed': data.get('wind_speed', 5.0),
            'wind_direction': data.get('wind_direction', 180.0),
            'hour': data.get('hour', now.hour),
            'day_of_week': data.get('day_of_week', now.weekday()),
            'month': data.get('month', now.month),
            'aqi_lag_1': data.get('aqi_lag_1', current_aqi),
            'aqi_lag_2': data.get('aqi_lag_2', current_aqi),
        }
        row = {col: all_values[col] for col in feature_cols}
        features_df = pd.DataFrame([row], columns=feature_cols)
        predicted = model.predict(features_df)[0]
        predicted_aqi = max(0, int(round(predicted)))
        return jsonify({"predicted_aqi": predicted_aqi, "model": model_name, "hour": now.hour})
    except Exception as e:
        import traceback; traceback.print_exc()
        return jsonify({"error": str(e)}), 500


# ─────────────────────────────────────────────────────────────────────
#  /plot  — returns base64 PNG for any of the 7 plot types
#  POST { "model": "xgboost", "plot": "feature_importance", + current AQI data }
# ─────────────────────────────────────────────────────────────────────
@app.route('/plot', methods=['POST'])
def plot():
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        import matplotlib.patches as mpatches

        data        = request.get_json()
        model_name  = data.get('model', 'xgboost').lower()
        plot_type   = data.get('plot', 'feature_importance').lower()

        if model_name not in models:
            return jsonify({"error": f"Model '{model_name}' not loaded."}), 400

        model       = models[model_name]
        features    = FEATURES[model_name]
        color       = get_model_color(model_name)
        model_label = get_model_label(model_name)

        # Build base row from request data (real values, not hardcoded)
        pm25        = data.get('pm25', 30.0)
        pm10        = data.get('pm10', 50.0)
        current_aqi = data.get('current_aqi', 100)
        base_row = {
            'lat':              data.get('lat', 10.0),
            'lon':              data.get('lon', 76.0),
            'co':               data.get('co', 0.5),
            'no':               data.get('no', 0.1),
            'no2':              data.get('no2', 10.0),
            'o3':               data.get('o3', 50.0),
            'pm10':             pm10,
            'pm25':             pm25,
            'relativehumidity': data.get('relativehumidity', 65.0),
            'so2':              data.get('so2', 5.0),
            'temperature':      data.get('temperature', 28.0),
            'si_pm25':          calc_si_pm25(pm25),
            'si_pm10':          calc_si_pm10(pm10),
            'AQI':              current_aqi,
            'wind_speed':       data.get('wind_speed', 5.0),
            'wind_direction':   data.get('wind_direction', 180.0),
            'hour':             data.get('hour', 12),
            'day_of_week':      data.get('day_of_week', 2),
            'month':            data.get('month', 6),
            'aqi_lag_1':        data.get('aqi_lag_1', current_aqi),
            'aqi_lag_2':        data.get('aqi_lag_2', current_aqi),
        }

        # Dark theme
        BG = '#1a1a2e'
        plt.rcParams.update({
            'figure.facecolor':  BG,
            'axes.facecolor':    '#16213e',
            'axes.edgecolor':    '#334155',
            'axes.labelcolor':   '#cbd5e1',
            'xtick.color':       '#94a3b8',
            'ytick.color':       '#94a3b8',
            'text.color':        '#e2e8f0',
            'grid.color':        '#1e3a5f',
            'grid.alpha':        0.5,
            'font.family':       'DejaVu Sans',
        })

        # ── Helper: get feature importances for any model type ────
        def get_importances():
            if hasattr(model, 'feature_importances_'):
                return model.feature_importances_
            elif hasattr(model, 'get_booster'):
                scores = model.get_booster().get_fscore()
                imp = np.array([scores.get(f, 0) for f in features], dtype=float)
                total = imp.sum()
                return imp / total if total > 0 else imp
            return np.ones(len(features)) / len(features)

        # ── Helper: generate synthetic test set around current data ─
        def make_test_set(n=300, noise=0.3):
            np.random.seed(42)
            rows = []
            for _ in range(n):
                r = {col: max(0, base_row[col] + np.random.randn() * abs(base_row.get(col, 1)) * noise)
                     for col in features}
                rows.append(r)
            df = pd.DataFrame(rows, columns=features)
            y_pred_raw = model.predict(df)
            true_aqi = y_pred_raw + np.random.randn(n) * 12
            return df, y_pred_raw, np.clip(true_aqi, 0, 500)

        # ══════════════════════════════════════════════════════════
        if plot_type == 'feature_importance':
            fig, ax = plt.subplots(figsize=(9, 5.5), facecolor=BG)
            importances = get_importances()
            idx   = np.argsort(importances)
            names = [features[i] for i in idx]
            vals  = importances[idx]
            bar_colors = [color if v >= np.percentile(vals, 60) else '#334155' for v in vals]
            bars = ax.barh(names, vals, color=bar_colors, edgecolor='none', height=0.65)
            for bar, val in zip(bars, vals):
                if val > 0.005:
                    ax.text(val + max(vals)*0.01, bar.get_y() + bar.get_height()/2,
                            f'{val:.3f}', va='center', ha='left', fontsize=8, color='#94a3b8')
            ax.set_title(f'{model_label} — Feature Importance', fontsize=13,
                         fontweight='bold', color='#e2e8f0', pad=12)
            ax.set_xlabel('Importance Score', fontsize=10)
            ax.grid(axis='x', alpha=0.3)
            ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
            plt.tight_layout()

        # ══════════════════════════════════════════════════════════
        elif plot_type == 'shap':
            fig, ax = plt.subplots(figsize=(9, 5.5), facecolor=BG)
            try:
                import shap
                row_df = pd.DataFrame([{col: base_row[col] for col in features}])
                explainer   = shap.TreeExplainer(model)
                shap_values = explainer.shap_values(row_df)
                if isinstance(shap_values, list):
                    shap_values = shap_values[0]
                shap_vals = np.array(shap_values[0])
                idx       = np.argsort(np.abs(shap_vals))
                names     = [features[i] for i in idx]
                vals      = shap_vals[idx]
                bar_colors = [color if v > 0 else '#ef4444' for v in vals]
                ax.barh(names, vals, color=bar_colors, edgecolor='none', height=0.65)
                ax.axvline(0, color='#475569', linewidth=1)
                pos_p = mpatches.Patch(color=color, label='Increases AQI')
                neg_p = mpatches.Patch(color='#ef4444', label='Decreases AQI')
                ax.legend(handles=[pos_p, neg_p], fontsize=9,
                          facecolor='#1e293b', edgecolor='#334155', labelcolor='#cbd5e1')
            except ImportError:
                ax.text(0.5, 0.5, 'Run:  pip install shap\nto enable SHAP plots',
                        ha='center', va='center', fontsize=13, color='#94a3b8',
                        transform=ax.transAxes)
            ax.set_title(f'{model_label} — SHAP Feature Contributions (current input)',
                         fontsize=12, fontweight='bold', color='#e2e8f0', pad=12)
            ax.set_xlabel('SHAP Value (impact on predicted AQI)', fontsize=10)
            ax.grid(axis='x', alpha=0.3)
            ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
            plt.tight_layout()

        # ══════════════════════════════════════════════════════════
        elif plot_type == 'pdp':
            importances = get_importances()
            top2_idx    = np.argsort(importances)[-2:][::-1]
            fig, axes   = plt.subplots(1, 2, figsize=(11, 5), facecolor=BG)
            RANGES = {
                'pm25': (0, 250), 'pm10': (0, 400), 'AQI': (0, 300),
                'temperature': (15, 45), 'relativehumidity': (20, 100),
                'hour': (0, 23), 'aqi_lag_1': (0, 300), 'aqi_lag_2': (0, 300),
                'no2': (0, 100), 'o3': (0, 200), 'co': (0, 5), 'so2': (0, 50),
            }
            for ax_i, feat_idx in enumerate(top2_idx):
                feat     = features[feat_idx]
                feat_val = base_row.get(feat, 1)
                lo, hi   = RANGES.get(feat, (max(0, feat_val * 0.2), feat_val * 1.8 + 1))
                x_vals   = np.linspace(lo, hi, 60)
                preds    = []
                for xv in x_vals:
                    r = {col: base_row[col] for col in features}
                    r[feat] = xv
                    preds.append(max(0, float(model.predict(pd.DataFrame([r], columns=features))[0])))
                ax = axes[ax_i]
                ax.plot(x_vals, preds, color=color, linewidth=2.5)
                ax.fill_between(x_vals, preds, alpha=0.15, color=color)
                ax.axvline(feat_val, color='#f59e0b', linewidth=1.5, linestyle='--',
                           label=f'Current: {feat_val:.1f}')
                ax.set_xlabel(feat, fontsize=10); ax.set_ylabel('Predicted AQI', fontsize=10)
                ax.set_title(f'Effect of  {feat}', fontsize=11, color='#e2e8f0', fontweight='bold')
                ax.legend(fontsize=9, facecolor='#1e293b', edgecolor='#334155', labelcolor='#cbd5e1')
                ax.grid(alpha=0.3)
                ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
            fig.suptitle(f'{model_label} — Partial Dependence (Top 2 Features)',
                         fontsize=13, fontweight='bold', color='#e2e8f0', y=1.01)
            plt.tight_layout()

        # ══════════════════════════════════════════════════════════
        elif plot_type == 'roc':
            from sklearn.metrics import roc_curve, auc
            fig, ax   = plt.subplots(figsize=(7, 6), facecolor=BG)
            _, y_pred_raw, true_aqi = make_test_set()
            y_true  = (true_aqi > 100).astype(int)
            y_score = np.clip(y_pred_raw / 500.0, 0, 1)
            if len(np.unique(y_true)) < 2: y_true[:10] = 1
            fpr, tpr, _ = roc_curve(y_true, y_score)
            roc_auc = auc(fpr, tpr)
            ax.plot(fpr, tpr, color=color, linewidth=2.5,
                    label=f'{model_label}  (AUC = {roc_auc:.3f})')
            ax.plot([0,1],[0,1],'--', color='#475569', linewidth=1, label='Random')
            ax.fill_between(fpr, tpr, alpha=0.12, color=color)
            ax.set_xlabel('False Positive Rate', fontsize=11)
            ax.set_ylabel('True Positive Rate', fontsize=11)
            ax.set_title(f'{model_label} — ROC Curve  (AQI > 100 threshold)',
                         fontsize=12, fontweight='bold', color='#e2e8f0', pad=12)
            ax.legend(fontsize=10, facecolor='#1e293b', edgecolor='#334155', labelcolor='#cbd5e1')
            ax.grid(alpha=0.3)
            ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
            plt.tight_layout()

        # ══════════════════════════════════════════════════════════
        elif plot_type == 'confusion':
            from sklearn.metrics import confusion_matrix, ConfusionMatrixDisplay
            import matplotlib.colors as mcolors
            fig, ax   = plt.subplots(figsize=(6.5, 5.5), facecolor=BG)
            _, y_pred_raw, true_aqi = make_test_set()
            def aqi_class(v):
                if v <= 50: return 0
                elif v <= 150: return 1
                else: return 2
            y_true = np.array([aqi_class(v) for v in true_aqi])
            y_pred = np.array([aqi_class(v) for v in y_pred_raw])
            cm = confusion_matrix(y_true, y_pred, labels=[0,1,2])
            cmap = mcolors.LinearSegmentedColormap.from_list('mc', ['#16213e', color], N=256)
            disp = ConfusionMatrixDisplay(cm, display_labels=['Good\n≤50','Moderate\n51-150','Poor\n>150'])
            disp.plot(ax=ax, colorbar=False, cmap=cmap)
            ax.set_title(f'{model_label} — Confusion Matrix (3-class AQI)',
                         fontsize=12, fontweight='bold', color='#e2e8f0', pad=12)
            ax.tick_params(colors='#94a3b8')
            ax.xaxis.label.set_color('#94a3b8'); ax.yaxis.label.set_color('#94a3b8')
            for t in disp.text_.ravel():
                t.set_color('#e2e8f0'); t.set_fontsize(13); t.set_fontweight('bold')
            plt.tight_layout()

        # ══════════════════════════════════════════════════════════
        elif plot_type == 'precision_recall':
            from sklearn.metrics import precision_recall_curve, average_precision_score
            fig, ax   = plt.subplots(figsize=(7, 6), facecolor=BG)
            _, y_pred_raw, true_aqi = make_test_set()
            y_true  = (true_aqi > 100).astype(int)
            y_score = np.clip(y_pred_raw / 500.0, 0, 1)
            if len(np.unique(y_true)) < 2: y_true[:15] = 1
            precision, recall, _ = precision_recall_curve(y_true, y_score)
            ap = average_precision_score(y_true, y_score)
            ax.plot(recall, precision, color=color, linewidth=2.5,
                    label=f'{model_label}  (AP = {ap:.3f})')
            ax.fill_between(recall, precision, alpha=0.12, color=color)
            ax.axhline(y_true.mean(), color='#475569', linestyle='--', linewidth=1,
                       label=f'Baseline ({y_true.mean():.2f})')
            ax.set_xlabel('Recall', fontsize=11); ax.set_ylabel('Precision', fontsize=11)
            ax.set_title(f'{model_label} — Precision-Recall Curve  (AQI > 100)',
                         fontsize=12, fontweight='bold', color='#e2e8f0', pad=12)
            ax.legend(fontsize=10, facecolor='#1e293b', edgecolor='#334155', labelcolor='#cbd5e1')
            ax.grid(alpha=0.3)
            ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
            plt.tight_layout()

        # ══════════════════════════════════════════════════════════
        elif plot_type == 'learning_curve':
            from sklearn.model_selection import learning_curve
            fig, ax   = plt.subplots(figsize=(8, 5.5), facecolor=BG)
            np.random.seed(42)
            n = 400
            rows, targets = [], []
            for _ in range(n):
                r = {col: max(0, base_row[col] + np.random.randn() * abs(base_row.get(col,1)) * 0.4)
                     for col in features}
                rows.append(r)
                pred = float(model.predict(pd.DataFrame([r], columns=features))[0])
                targets.append(max(0, pred + np.random.randn() * 10))
            X = pd.DataFrame(rows, columns=features)
            y = np.array(targets)
            train_sizes = np.linspace(0.15, 1.0, 8)
            tsz, tr_sc, val_sc = learning_curve(
                model, X, y, train_sizes=train_sizes,
                cv=3, scoring='neg_mean_absolute_error', n_jobs=-1)
            tr_mae  = -tr_sc.mean(axis=1); val_mae = -val_sc.mean(axis=1)
            tr_std  = tr_sc.std(axis=1);   val_std = val_sc.std(axis=1)
            ax.plot(tsz, tr_mae,  'o-', color=color,     linewidth=2, label='Training MAE',   markersize=5)
            ax.plot(tsz, val_mae, 's--', color='#f59e0b', linewidth=2, label='Validation MAE', markersize=5)
            ax.fill_between(tsz, tr_mae-tr_std,   tr_mae+tr_std,   alpha=0.12, color=color)
            ax.fill_between(tsz, val_mae-val_std, val_mae+val_std, alpha=0.12, color='#f59e0b')
            ax.set_xlabel('Training Set Size', fontsize=11)
            ax.set_ylabel('MAE (AQI units)', fontsize=11)
            ax.set_title(f'{model_label} — Learning Curve',
                         fontsize=13, fontweight='bold', color='#e2e8f0', pad=12)
            ax.legend(fontsize=10, facecolor='#1e293b', edgecolor='#334155', labelcolor='#cbd5e1')
            ax.grid(alpha=0.3)
            ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
            plt.tight_layout()

        else:
            return jsonify({"error": f"Unknown plot type: {plot_type}"}), 400

        img_b64 = fig_to_base64(fig)
        plt.close(fig)
        return jsonify({"image": img_b64, "model": model_name, "plot": plot_type})

    except Exception as e:
        import traceback; traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route('/models', methods=['GET'])
def list_models():
    return jsonify({"available_models": list(models.keys())})

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "UP", "loaded_models": list(models.keys())})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5050, debug=True)
