import { NextResponse, type NextRequest } from "next/server";
import { backendJson } from "@/lib/backend";
import { SCENARIO_META } from "@/lib/categories";
import type { Place, Scenario } from "@/types/place";

// GET /api/urgent?scenario&lat&lng
// 시나리오의 카테고리별 nearest(kNN)를 서버에서 팬아웃 → id 중복 제거 → 거리순 → 상위 5.
// (nearest 는 category 단일 파라미터라 다중 카테고리 시나리오는 서버 병합이 필요.)
const RESULT_LIMIT = 5;
const PER_CATEGORY_LIMIT = 5;

export async function GET(request: NextRequest) {
  const sp = request.nextUrl.searchParams;
  const scenario = sp.get("scenario") as Scenario | null;
  const lat = sp.get("lat");
  const lng = sp.get("lng");

  if (!scenario || !SCENARIO_META[scenario] || !lat || !lng) {
    return NextResponse.json({ error: "bad_request", message: "scenario, lat, lng required" }, { status: 400 });
  }

  const { categories } = SCENARIO_META[scenario];

  try {
    const batches = await Promise.all(
      categories.map((category) => {
        const qs = new URLSearchParams({ lat, lng, category, limit: String(PER_CATEGORY_LIMIT) });
        return backendJson<Place[]>("/places/nearest", qs.toString());
      }),
    );

    const byId = new Map<number, Place>();
    for (const place of batches.flat()) {
      const existing = byId.get(place.id);
      if (!existing || (place.distanceM ?? Infinity) < (existing.distanceM ?? Infinity)) {
        byId.set(place.id, place);
      }
    }

    const merged = [...byId.values()]
      .sort((a, b) => (a.distanceM ?? Infinity) - (b.distanceM ?? Infinity))
      .slice(0, RESULT_LIMIT);

    return NextResponse.json(merged);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return NextResponse.json({ error: "upstream_unreachable", message }, { status: 502 });
  }
}
