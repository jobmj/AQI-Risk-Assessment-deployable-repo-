import os
import gdown
import joblib
from flask import Flask, jsonify, request
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

# ── Download Random Forest model from Google Drive ──────
RF_MODEL_PATH = 'ml/models/randomforest_model.pkl'

os.makedirs('ml/models', exist_ok=True)

if not os.path.exists(RF_MODEL_PATH):
    print("Downloading Random Forest model...")
    gdown.download(
        'https://drive.google.com/uc?id=1JxOlKNPTLzzSgrjS21tb7zqR71Ftcu_m',
        RF_MODEL_PATH,
        quiet=False
    )
    print("Download complete ✓")

# ── Load all 3 models ───────────────────────────────────
print("Loading models...")
models = {
    'randomforest': joblib.load('ml/models/randomforest_model.pkl'),
    'model1':       joblib.load('ml/models/model1.pkl'),
    'model2':       joblib.load('ml/models/model2.pkl'),
}
print("All models loaded ✓")

# ── Routes ──────────────────────────────────────────────
@app.route('/health')
def health():
    return jsonify({'status': 'ok', 'models': list(models.keys())})

@app.route('/predict/<model_name>', methods=['POST'])
def predict(model_name):
    if model_name not in models:
        return jsonify({'error': 'Model not found'}), 404
    data = request.get_json()
    prediction = models[model_name].predict([data['features']])
    return jsonify({'prediction': prediction.tolist()})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)
