import type { NextRequest } from "next/server";
import { proxyAuthed } from "@/lib/backend";

// PATCH /api/notifications/rules/{id} → 규칙 활성 토글 (로그인 필요)
export async function PATCH(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return proxyAuthed("PATCH", `/notifications/rules/${encodeURIComponent(id)}`, request);
}

// DELETE /api/notifications/rules/{id} → 규칙 삭제 (로그인 필요)
export async function DELETE(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return proxyAuthed("DELETE", `/notifications/rules/${encodeURIComponent(id)}`, request);
}
