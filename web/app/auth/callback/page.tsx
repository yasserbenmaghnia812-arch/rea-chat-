"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { supabase } from "@/lib/supabase";

export default function AuthCallback() {
  const router = useRouter();
  const [message, setMessage] = useState("جارٍ تأكيد تسجيل الدخول...");

  useEffect(() => {
    let active = true;
    (async () => {
      const url = new URL(window.location.href);
      const code = url.searchParams.get("code");
      const authError = url.searchParams.get("error_description") || url.searchParams.get("error");
      if (authError) {
        if (active) setMessage(decodeURIComponent(authError.replace(/\+/g, " ")));
        return;
      }
      if (code) {
        const { error } = await supabase.auth.exchangeCodeForSession(code);
        if (error) {
          if (active) setMessage(error.message);
          return;
        }
      }
      const { data } = await supabase.auth.getSession();
      if (!data.session) {
        if (active) setMessage("لم تكتمل جلسة الدخول. ارجع وسجّل الدخول مرة أخرى.");
        return;
      }
      if (active) router.replace("/");
    })();
    return () => { active = false; };
  }, [router]);

  return <main className="center"><div className="loader" /><p>{message}</p></main>;
}
