from fastapi import FastAPI
from pydantic import BaseModel, Field

from .regimen import map_biomarker_delta

app = FastAPI(
    title="DermaTrack AI Agentic Backend",
    version="0.1.0",
    description="Ingredient and affiliate orchestration only. Raw biometrics are not accepted.",
)


class BiomarkerDeltaRequest(BaseModel):
    days_observed: int = Field(ge=0)
    erythema_delta: float
    melanin_delta: float
    texture_delta: float
    acne_delta: int
    current_actives: list[str] = Field(default_factory=list)


class IngredientDecision(BaseModel):
    trigger: str
    ingredient_needs: list[str]
    clinical_note: str


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/regimen/ingredient-decision", response_model=IngredientDecision)
def ingredient_decision(payload: BiomarkerDeltaRequest) -> IngredientDecision:
    decision = map_biomarker_delta(
        days_observed=payload.days_observed,
        erythema_delta=payload.erythema_delta,
        melanin_delta=payload.melanin_delta,
        texture_delta=payload.texture_delta,
        acne_delta=payload.acne_delta,
        current_actives=payload.current_actives,
    )
    return IngredientDecision(**decision)
