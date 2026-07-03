import type { NextRequest } from "next/server";
import { proxy } from "@/lib/backend";

// GET /api/places/nearest?lat&lng&category&limit → ALB /places/nearest (kNN)
export function GET(request: NextRequest) {
  return proxy("/places/nearest", request.nextUrl.searchParams.toString());
}
