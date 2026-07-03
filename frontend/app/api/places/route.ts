import type { NextRequest } from "next/server";
import { proxy } from "@/lib/backend";

// GET /api/places?lat&lng&radius&limit  또는  ?bounds=w,s,e,n  → ALB /places
export function GET(request: NextRequest) {
  return proxy("/places", request.nextUrl.searchParams.toString());
}
