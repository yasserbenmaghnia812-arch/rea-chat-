import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";

export const runtime = "nodejs";
const repoOwner = "yasserbenmaghnia812-arch";
const repoName = "rea-chat-";
const workflow = "rea-analysis.yml";

export async function POST(request: NextRequest) {
  const token = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return NextResponse.json({ error: "يلزم تسجيل الدخول." }, { status: 401 });
  const supabase = createClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false }
  });
  const { data: auth, error: authError } = await supabase.auth.getUser(token);
  if (authError || !auth.user) return NextResponse.json({ error: "جلسة غير صالحة." }, { status: 401 });
  const { project_id, task_id, storage_path } = await request.json();
  if (typeof project_id !== "string" || typeof task_id !== "string" || typeof storage_path !== "string") return NextResponse.json({ error: "بيانات المهمة غير صحيحة." }, { status: 400 });
  const [{ data: project }, { data: task }] = await Promise.all([
    supabase.from("projects").select("id").eq("id", project_id).eq("user_id", auth.user.id).maybeSingle(),
    supabase.from("analysis_tasks").select("id").eq("id", task_id).eq("project_id", project_id).eq("user_id", auth.user.id).maybeSingle()
  ]);
  if (!project || !task) return NextResponse.json({ error: "المشروع أو المهمة غير موجودة لهذا الحساب." }, { status: 404 });
  const { data: signed, error: signError } = await supabase.storage.from("apk-temp").createSignedUrl(storage_path, 600);
  if (signError || !signed?.signedUrl) return NextResponse.json({ error: "تعذر إنشاء رابط رفع مؤقت: " + (signError?.message || "") }, { status: 500 });
  const githubToken = process.env.GITHUB_ACTIONS_TOKEN;
  if (!githubToken) return NextResponse.json({ error: "يلزم إضافة GITHUB_ACTIONS_TOKEN إلى إعدادات Vercel قبل تشغيل REA." }, { status: 503 });
  const dispatch = await fetch(`https://api.github.com/repos/${repoOwner}/${repoName}/actions/workflows/${workflow}/dispatches`, {
    method: "POST",
    headers: { Authorization: `Bearer ${githubToken}`, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json" },
    body: JSON.stringify({ ref: "main", inputs: { apk_url: signed.signedUrl, task_id, project_id } })
  });
  if (!dispatch.ok) {
    const details = await dispatch.text();
    return NextResponse.json({ error: `GitHub رفض تشغيل المهمة (${dispatch.status}): ${details.slice(0, 400)}` }, { status: 502 });
  }
  await supabase.from("analysis_tasks").update({ status: "running", progress: 5 }).eq("id", task_id).eq("user_id", auth.user.id);
  await supabase.from("projects").update({ status: "analyzing" }).eq("id", project_id).eq("user_id", auth.user.id);
  return NextResponse.json({ ok: true, task_id, message: "تم إرسال مهمة REA إلى GitHub Actions." });
}
