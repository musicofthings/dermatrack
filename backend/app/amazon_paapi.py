import base64
import datetime as dt
import hashlib
import hmac
import json
import os
from dataclasses import dataclass

import httpx


@dataclass
class AmazonProduct:
    asin: str
    title: str
    rating: float | None
    rating_count: int | None
    price: str | None
    url: str | None


class AmazonPaapiClient:
    def __init__(self) -> None:
        self.access_key = os.getenv("AMAZON_PAAPI_ACCESS_KEY", "")
        self.secret_key = os.getenv("AMAZON_PAAPI_SECRET_KEY", "")
        self.partner_tag = os.getenv("AMAZON_PAAPI_PARTNER_TAG", "")
        self.host = os.getenv("AMAZON_PAAPI_HOST", "webservices.amazon.in")
        self.region = os.getenv("AMAZON_PAAPI_REGION", "eu-west-1")
        self.service = "ProductAdvertisingAPI"
        self.target = "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems"
        self.uri = "/paapi5/searchitems"

    def configured(self) -> bool:
        return bool(self.access_key and self.secret_key and self.partner_tag)

    async def search(self, query: str) -> list[AmazonProduct]:
        if not self.configured():
            return []
        payload = {
            "Keywords": query,
            "Marketplace": "www.amazon.in",
            "PartnerTag": self.partner_tag,
            "PartnerType": "Associates",
            "ItemCount": 10,
            "Resources": [
                "ItemInfo.Title",
                "CustomerReviews.StarRating",
                "CustomerReviews.Count",
                "Offers.Listings.Price.DisplayAmount",
                "DetailPageURL",
            ],
        }
        body = json.dumps(payload, separators=(",", ":"))
        amz_date = dt.datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
        date_stamp = amz_date[:8]
        authorization = self._authorization_header(body, amz_date, date_stamp)
        headers = {
            "content-encoding": "amz-1.0",
            "content-type": "application/json; charset=UTF-8",
            "host": self.host,
            "x-amz-date": amz_date,
            "x-amz-target": self.target,
            "Authorization": authorization,
        }
        url = f"https://{self.host}{self.uri}"
        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.post(url, headers=headers, content=body)
            response.raise_for_status()
            data = response.json()
        items = data.get("SearchResult", {}).get("Items", []) or []
        parsed: list[AmazonProduct] = []
        for item in items:
            title = (
                item.get("ItemInfo", {})
                .get("Title", {})
                .get("DisplayValue")
            )
            asin = item.get("ASIN")
            if not asin or not title:
                continue
            reviews = item.get("CustomerReviews", {})
            offers = item.get("Offers", {}).get("Listings", []) or []
            price = offers[0].get("Price", {}).get("DisplayAmount") if offers else None
            parsed.append(
                AmazonProduct(
                    asin=asin,
                    title=title,
                    rating=reviews.get("StarRating"),
                    rating_count=reviews.get("Count"),
                    price=price,
                    url=item.get("DetailPageURL"),
                )
            )
        return parsed

    def _authorization_header(self, payload: str, amz_date: str, date_stamp: str) -> str:
        canonical_headers = (
            f"content-encoding:amz-1.0\n"
            f"content-type:application/json; charset=UTF-8\n"
            f"host:{self.host}\n"
            f"x-amz-date:{amz_date}\n"
            f"x-amz-target:{self.target}\n"
        )
        signed_headers = "content-encoding;content-type;host;x-amz-date;x-amz-target"
        payload_hash = hashlib.sha256(payload.encode("utf-8")).hexdigest()
        canonical_request = "\n".join(
            [
                "POST",
                self.uri,
                "",
                canonical_headers,
                signed_headers,
                payload_hash,
            ]
        )
        algorithm = "AWS4-HMAC-SHA256"
        credential_scope = f"{date_stamp}/{self.region}/{self.service}/aws4_request"
        string_to_sign = "\n".join(
            [
                algorithm,
                amz_date,
                credential_scope,
                hashlib.sha256(canonical_request.encode("utf-8")).hexdigest(),
            ]
        )
        signing_key = self._signature_key(date_stamp)
        signature = hmac.new(
            signing_key,
            string_to_sign.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()
        return (
            f"{algorithm} "
            f"Credential={self.access_key}/{credential_scope}, "
            f"SignedHeaders={signed_headers}, "
            f"Signature={signature}"
        )

    def _signature_key(self, date_stamp: str) -> bytes:
        k_date = hmac.new(
            f"AWS4{self.secret_key}".encode("utf-8"),
            date_stamp.encode("utf-8"),
            hashlib.sha256,
        ).digest()
        k_region = hmac.new(k_date, self.region.encode("utf-8"), hashlib.sha256).digest()
        k_service = hmac.new(k_region, self.service.encode("utf-8"), hashlib.sha256).digest()
        return hmac.new(k_service, b"aws4_request", hashlib.sha256).digest()
