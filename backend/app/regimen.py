def map_biomarker_delta(
    *,
    days_observed: int,
    erythema_delta: float,
    melanin_delta: float,
    texture_delta: float,
    acne_delta: int,
    current_actives: list[str],
) -> dict:
    actives = " ".join(current_actives).lower()

    if days_observed >= 21 and melanin_delta >= -1.0 and "niacinamide" in actives:
        return {
            "trigger": "melanin_stagnant_after_21_days",
            "ingredient_needs": ["alpha arbutin", "azelaic acid", "tranexamic acid"],
            "clinical_note": "PIH marker has not improved enough to justify staying on niacinamide alone.",
        }

    if erythema_delta > 4.0:
        return {
            "trigger": "erythema_increase",
            "ingredient_needs": ["ceramides", "panthenol", "colloidal oatmeal"],
            "clinical_note": "Inflammation marker increased. Favor barrier repair before stronger actives.",
        }

    if acne_delta > 0:
        return {
            "trigger": "acne_lesion_increase",
            "ingredient_needs": ["benzoyl peroxide", "salicylic acid"],
            "clinical_note": "Lesion count rose. Screen inventory for occlusive or comedogenic products.",
        }

    return {
        "trigger": "no_pivot",
        "ingredient_needs": [],
        "clinical_note": "No ingredient pivot triggered from current biomarker deltas.",
    }
