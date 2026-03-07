import joblib
import os

folder = r'C:\Users\Fahad\Desktop\my works\Java AP project\AQI-Risk-Assessment\ml\models'

for name, file in [("xgboost","xgboost_model.pkl"), ("randomforest","randomforest_model.pkl"), ("lightgbm","lightgbm_model.pkl")]:
    model = joblib.load(os.path.join(folder, file))
    print(f"\n{name}: {model.feature_names_in_.tolist()}")