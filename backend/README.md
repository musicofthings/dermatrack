# DermaTrack AI Backend

FastAPI service for non-biometric agentic orchestration:

- Ingredient need to product query mapping.
- Amazon PA-API India requests using server-side credentials.
- No raw face images or biometric frames should be accepted by this service.

## Run

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Environment variables expected for PA-API wiring:

- `AMAZON_PAAPI_ACCESS_KEY`
- `AMAZON_PAAPI_SECRET_KEY`
- `AMAZON_PAAPI_PARTNER_TAG`
- `AMAZON_PAAPI_HOST`, default `webservices.amazon.in`
- `AMAZON_PAAPI_REGION`, default `eu-west-1`
