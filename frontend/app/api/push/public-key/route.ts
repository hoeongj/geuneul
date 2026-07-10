import { proxy } from "@/lib/backend";

// GET /api/push/public-key → ALB /push/public-key (VAPID 공개키 + 활성 여부, 공개)
export function GET() {
  return proxy("/push/public-key", "");
}
