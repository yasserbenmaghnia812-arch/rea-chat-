"use client";

import { useEffect, useRef, useState } from "react";
import { supabase } from "@/lib/supabase";

type ChatMessage = { id: string; role: "user" | "assistant" | "system" | "tool"; content: string; created_at?: string };
type SessionUser = { id: string; email?: string };

export default function Home() {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [authMode, setAuthMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [jobNotice, setJobNotice] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setUser(data.session?.user ? { id: data.session.user.id, email: data.session.user.email } : null);
      setLoading(false);
    });
    const { data } = supabase.auth.onAuthStateChange((_event, session) => {
      setUser(session?.user ? { id: session.user.id, email: session.user.email } : null);
    });
    return () => data.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (!user) return;
    let active = true;
    (async () => {
      const { data: existing } = await supabase.from("conversations").select("id").eq("user_id", user.id).order("updated_at", { ascending: false }).limit(1).maybeSingle();
      let id = existing?.id as string | undefined;
      if (!id) {
        const { data, error } = await supabase.from("conversations").insert({ user_id: user.id, title: "محادثة جديدة" }).select("id").single();
        if (error) { if (active) setNotice(error.message); return; }
        id = data.id;
      }
      if (!id) { if (active) setNotice("تعذر إنشاء المحادثة."); return; }
      if (!active) return;
      setConversationId(id);
      const { data: history, error } = await supabase.from("messages").select("id,role,content,created_at").eq("conversation_id", id).order("created_at", { ascending: true }).limit(100);
      if (error) setNotice(error.message);
      else if (active) setMessages((history ?? []) as ChatMessage[]);
    })();
    return () => { active = false; };
  }, [user]);

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [messages]);

  async function authenticate(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setNotice("");
    try {
      const result = authMode === "login"
        ? await supabase.auth.signInWithPassword({ email: email.trim(), password })
        : await supabase.auth.signUp({ email: email.trim(), password, options: { data: { full_name: email.split("@")[0] } } });
      if (result.error) throw result.error;
      if (authMode === "signup" && !result.data.session) setNotice("تم إنشاء الحساب. تحقق من بريدك الإلكتروني لتأكيده ثم سجّل الدخول.");
      else setNotice("تم تسجيل الدخول.");
    } catch (error) { setNotice(error instanceof Error ? error.message : "تعذر إكمال العملية."); }
    finally { setBusy(false); }
  }

  async function sendMessage(event: React.FormEvent) {
    event.preventDefault();
    const content = draft.trim();
    if (!content || !user || !conversationId || busy) return;
    setDraft(""); setBusy(true); setNotice("");
    const optimistic: ChatMessage = { id: crypto.randomUUID(), role: "user", content };
    setMessages(previous => [...previous, optimistic]);
    try {
      const { data: inserted, error } = await supabase.from("messages").insert({ conversation_id: conversationId, user_id: user.id, role: "user", content }).select("id,role,content,created_at").single();
      if (error) throw error;
      const { data: sessionData } = await supabase.auth.getSession();
      const response = await fetch("/api/chat", { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${sessionData.session?.access_token ?? ""}` }, body: JSON.stringify({ conversation_id: conversationId, message: content }) });
      const body = await response.json();
      if (!response.ok) throw new Error(body.error || "فشل رد المساعد.");
      setMessages(previous => [...previous.filter(item => item.id !== optimistic.id), inserted as ChatMessage, body.message as ChatMessage]);
    } catch (error) {
      setMessages(previous => previous.filter(item => item.id !== optimistic.id));
      setDraft(content);
      setNotice(error instanceof Error ? error.message : "حدث خطأ.");
    } finally { setBusy(false); }
  }

  async function startAnalysis() {
    if (!selectedFile || !user || busy) return;
    if (!selectedFile.name.toLowerCase().endsWith(".apk")) { setJobNotice("اختر ملف APK فقط."); return; }
    if (selectedFile.size > 100 * 1024 * 1024) { setJobNotice("الحد الأقصى لحجم الملف 100 ميغابايت."); return; }
    setBusy(true); setJobNotice("جارٍ رفع الملف بأمان...");
    try {
      const path = `${user.id}/${crypto.randomUUID()}-${selectedFile.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
      const { error: uploadError } = await supabase.storage.from("apk-temp").upload(path, selectedFile, { contentType: "application/vnd.android.package-archive", upsert: false });
      if (uploadError) throw uploadError;
      const { data: project, error: projectError } = await supabase.from("projects").insert({ user_id: user.id, name: selectedFile.name, original_filename: selectedFile.name, storage_path: path }).select("id").single();
      if (projectError) throw projectError;
      const { data: task, error: taskError } = await supabase.from("analysis_tasks").insert({ project_id: project.id, user_id: user.id, task_type: "analyze", status: "queued", input: { filename: selectedFile.name } }).select("id").single();
      if (taskError) throw taskError;
      const { data: sessionData } = await supabase.auth.getSession();
      const response = await fetch("/api/rea/start", { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${sessionData.session?.access_token ?? ""}` }, body: JSON.stringify({ project_id: project.id, task_id: task.id, storage_path: path }) });
      const body = await response.json();
      if (!response.ok) throw new Error(body.error || "تعذر تشغيل REA.");
      setJobNotice(`تم إرسال المهمة إلى REA. رقم المهمة: ${task.id}`);
      setSelectedFile(null);
    } catch (error) { setJobNotice(error instanceof Error ? error.message : "فشل رفع الملف أو بدء التحليل."); }
    finally { setBusy(false); }
  }

  if (loading) return <main className="center"><div className="loader" /> جارٍ التحميل...</main>;

  if (!user) return (
    <main className="auth-shell">
      <section className="auth-card">
        <div className="brand-mark">R</div>
        <h1>REA Chat</h1>
        <p className="muted">مساعد الذكاء الاصطناعي للهندسة العكسية</p>
        <form onSubmit={authenticate} className="auth-form">
          <label>البريد الإلكتروني<input type="email" required autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="name@example.com" /></label>
          <label>كلمة المرور<input type="password" required minLength={6} autoComplete={authMode === "login" ? "current-password" : "new-password"} value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" /></label>
          <button className="primary" disabled={busy}>{busy ? "جارٍ التنفيذ..." : authMode === "login" ? "تسجيل الدخول" : "إنشاء حساب جديد"}</button>
        </form>
        <button className="text-button" onClick={() => { setAuthMode(authMode === "login" ? "signup" : "login"); setNotice(""); }}>
          {authMode === "login" ? "ليس لديك حساب؟ إنشاء حساب جديد" : "لديك حساب؟ تسجيل الدخول"}
        </button>
        {notice && <p className="notice">{notice}</p>}
        <p className="tiny">تسجيل الدخول محمي بواسطة Supabase Auth.</p>
      </section>
    </main>
  );

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand"><div className="brand-mark small">R</div><div><strong>REA Chat</strong><span>مساعد الهندسة العكسية</span></div></div>
        <div className="user-area"><span>{user.email}</span><button className="ghost" onClick={() => supabase.auth.signOut()}>خروج</button></div>
      </header>
      <section className="workspace">
        <aside className="sidebar">
          <button className="new-chat" onClick={async () => {
            const { data, error } = await supabase.from("conversations").insert({ user_id: user.id, title: "محادثة جديدة" }).select("id").single();
            if (error) setNotice(error.message); else { setConversationId(data.id); setMessages([]); }
          }}>＋ محادثة جديدة</button>
          <div className="side-label">الأدوات</div>
          <div className="side-item active">◉ دردشة الذكاء الاصطناعي</div>
          <div className="side-item">⌁ تحليل ملفات APK</div>
          <div className="side-note">كل مشروع ونتائجه محفوظة في حسابك.</div>
        </aside>
        <section className="chat-area">
          <div className="chat-heading"><div><h2>كيف يمكنني مساعدتك؟</h2><p>اسأل عن تحليل APK أو ابدأ مهمة REA جديدة.</p></div><span className="status-pill">● متصل بقاعدة البيانات</span></div>
          <div className="messages" ref={scrollRef}>
            {messages.length === 0 && <div className="welcome"><div className="welcome-icon">✳</div><h3>ابدأ محادثة جديدة</h3><p>يمكنني مساعدتك في فهم نتائج التحليل وتخطيط خطوات الفحص.</p></div>}
            {messages.map(message => <div key={message.id} className={`message ${message.role === "user" ? "mine" : "theirs"}`}><div className="message-role">{message.role === "user" ? "أنت" : "REA AI"}</div><div>{message.content}</div></div>)}
            {busy && <div className="typing">المساعد يعمل...</div>}
          </div>
          {notice && <div className="notice inline-notice">{notice}</div>}
          <form className="composer" onSubmit={sendMessage}><textarea value={draft} onChange={e => setDraft(e.target.value)} placeholder="اكتب رسالتك..." rows={2} /><button className="primary send" disabled={busy || !draft.trim()}>إرسال ↑</button></form>
          <section className="upload-panel">
            <div><strong>تحليل تطبيق APK باستخدام REA</strong><p>ارفع ملفاً تملك حق تحليله. التنفيذ يتم في GitHub Actions المعزول.</p></div>
            <div className="upload-row"><input type="file" accept=".apk,application/vnd.android.package-archive" onChange={e => setSelectedFile(e.target.files?.[0] ?? null)} /><button className="secondary" onClick={startAnalysis} disabled={busy || !selectedFile}>رفع وبدء التحليل</button></div>
            {jobNotice && <p className="notice">{jobNotice}</p>}
          </section>
          <p className="disclaimer">لا تنفّذ REA أي تعديل على الملفات. التعديلات المقترحة تتطلب موافقتك المنفصلة.</p>
        </section>
      </section>
    </main>
  );
}
