import { NextResponse, type NextRequest } from "next/server";
import { backendJson } from "@/lib/backend";
import { SCENARIO_META } from "@/lib/categories";
import type { Place, Scenario } from "@/types/place";

// GET /api/urgent?scenario&lat&lng
// 백엔드 /recommendations(ADR-0008)로 승격 — survival_score에 시나리오 가중을 얹은 정식 랭킹.
// (기존엔 카테고리별 nearest 를 프록시가 팬아웃·거리순 병합했다. 이제 카테고리 집합·가중·재랭킹은
//  백엔드가 소유하고, 프록시는 결과를 UI가 쓰는 Place[] 로 평탄화하며 matchScore·reason 을 얹는다.)
const RESULT_LIMIT = 5;

interface Recommendation {
  place: Place;
  matchScore: number;
  reason: string;
}

export async function GET(request: NextRequest) {
  const sp = request.nextUrl.searchParams;
  const scenario = sp.get("scenario") as Scenario | null;
  const lat = sp.get("lat");
  const lng = sp.get("lng");

  if (!scenario || !SCENARIO_META[scenario] || !lat || !lng) {
    return NextResponse.json({ error: "bad_request", message: "scenario, lat, lng required" }, { status: 400 });
  }

  try {
    const qs = new URLSearchParams({ scenario, lat, lng, limit: String(RESULT_LIMIT) });
    const recs = await backendJson<Recommendation[]>("/recommendations", qs.toString());
    // 랭킹(matchScore 순)은 백엔드가 확정 — 프록시는 순서를 보존하며 근거만 각 장소에 접합.
    const places: Place[] = recs.map((r) => ({ ...r.place, matchScore: r.matchScore, reason: r.reason }));
    return NextResponse.json(places);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return NextResponse.json({ error: "upstream_unreachable", message }, { status: 502 });
  }
}
