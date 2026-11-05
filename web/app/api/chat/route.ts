import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";

export const runtime = "nodejs";
const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;

function clientFor(token: string) {
  return createClient(url, key, { global: { headers: { Authorization: `Bearer ${token}` } }, auth: { persistSession: false, autoRefreshToken: false } });
}

async function askGemini(messages: { role: string; content: string }[]) {
  const apiKey = process.env.GEMINI_API_KEY!;
  const contents = messages.filter(m => m.role === "user" || m.role === "assistant").map(m => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: m.content }] }));
  const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${apiKey}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ systemInstruction: { parts: [{ text: "أنت مساعد REA Chat. أجب بالعربية بوضوح. لا تدّع تنفيذ تحليل أو تعديل لم يحدث. اطلب موافقة صريحة قبل اقتراح تطبيق أي تغييرات." }] }, contents }) });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error?.message || "Gemini request failed");
  return data.candidates?.[0]?.content?.parts?.map((p: {text?:string}) => p.text || "").join("") || "لم يصل رد من النموذج.";
}

async function askGroq(messages: { role: string; content: string }[]) {
  const response = await fetch("https://api.groq.com/openai/v1/chat/completions", { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${process.env.GROQ_API_KEY}` }, body: JSON.stringify({ model: "llama-3.3-70b-versatile", messages: [{ role: "system", content: "أنت مساعد REA Chat. أجب بالعربية. لا تدّع تنفيذ عمل لم يحدث، واطلب موافقة قبل تطبيق التعديلات." }, ...messages.map(m => ({ role: m.role, content: m.content }))] }) });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error?.message || "Groq request failed");
  return data.choices?.[0]?.message?.content || "لم يصل رد من النموذج.";
}

async function askOpenRouter(messages: { role: string; content: string }[]) {
  const response = await fetch("https://openrouter.ai/api/v1/chat/completions", { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${process.env.OPENROUTER_API_KEY}`, "HTTP-Referer": process.env.NEXT_PUBLIC_APP_URL || "https://rea-chat.vercel.app", "X-Title": "REA Chat" }, body: JSON.stringify({ model: "openrouter/free", messages: [{ role: "system", content: "أنت مساعد REA Chat. أجب بالعربية. لا تدّع تنفيذ عمل لم يحدث، واطلب موافقة قبل تطبيق التعديلات." }, ...messages.map(m => ({ role: m.role, content: m.content }))] }) });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error?.message || "OpenRouter request failed");
  return data.choices?.[0]?.message?.content || "لم يصل رد من النموذج.";
}

export async function POST(request: NextRequest) {
  const token = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return NextResponse.json({ error: "يلزم تسجيل الدخول." }, { status: 401 });
  const supabase = clientFor(token);
  const { data: auth, error: authError } = await supabase.auth.getUser(token);
  if (authError || !auth.user) return NextResponse.json({ error: "جلسة غير صالحة." }, { status: 401 });
  const body = await request.json();
  if (typeof body.message !== "string" || !body.message.trim() || typeof body.conversation_id !== "string") return NextResponse.json({ error: "بيانات الطلب غير صحيحة." }, { status: 400 });
  const { data: conversation } = await supabase.from("conversations").select("id").eq("id", body.conversation_id).eq("user_id", auth.user.id).maybeSingle();
  if (!conversation) return NextResponse.json({ error: "المحادثة غير موجودة." }, { status: 404 });
  const { data: history } = await supabase.from("messages").select("role,content").eq("conversation_id", conversation.id).order("created_at", { ascending: true }).limit(20);
  const messages = [...(history || []).filter(m => m.role === "user" || m.role === "assistant")];
  if (messages[messages.length - 1]?.role !== "user" || messages[messages.length - 1]?.content !== body.message) messages.push({ role: "user", content: body.message });
  const boundedMessages = messages.slice(-20);
  const providers = [
    ["Gemini", process.env.GEMINI_API_KEY, askGemini],
    ["Groq", process.env.GROQ_API_KEY, askGroq],
    ["OpenRouter", process.env.OPENROUTER_API_KEY, askOpenRouter]
  ] as const;
  let answer = "";
  let used = "";
  const failures: string[] = [];
  for (const [name, secret, ask] of providers) {
    if (!secret) continue;
    try { answer = await ask(boundedMessages); used = name; break; }
    catch (error) { failures.push(`${name}: ${error instanceof Error ? error.message : "failed"}`); }
  }
  if (!answer) return NextResponse.json({ error: providers.every(p => !p[1]) ? "لم تتم إضافة مفاتيح نماذج الذكاء الاصطناعي المجانية إلى Vercel بعد." : `تعذر الاتصال بمزودي الذكاء الاصطناعي: ${failures.join(" | ")}` }, { status: 503 });
  const { data: saved, error: saveError } = await supabase.from("messages").insert({ conversation_id: conversation.id, user_id: auth.user.id, role: "assistant", content: answer }).select("id,role,content,created_at").single();
  if (saveError) return NextResponse.json({ error: "تم إنشاء الرد لكن تعذر حفظه: " + saveError.message }, { status: 500 });
  await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", conversation.id);
  return NextResponse.json({ message: saved, provider: used });
}
