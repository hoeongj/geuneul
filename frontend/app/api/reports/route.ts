import type { NextRequest } from "next/server";
import { proxyPost } from "@/lib/backend";

// POST /api/reports → ALB /reports (익명 휘발성 제보). 클라이언트 IP를 XFF로 보존.
export function POST(request: NextRequest) {
  return proxyPost("/reports", request);
}
