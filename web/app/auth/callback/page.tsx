"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { supabase } from "@/lib/supabase";

export default function AuthCallback() {
  const router = useRouter();
  const [message, setMessage] = useState("جارٍ تأكيد تسجيل الدخول باستخدام Google...");

  useEffect(() => {
    let active = true;

    async function finishSignIn() {
      try {
        // supabase-js has detectSessionInUrl enabled and handles the PKCE
        // code exchange during initialize(). Do not exchange the same code
        // again here: doing so can consume the verifier twice and fail login.
        const { error: initError } = await supabase.auth.initialize();
        if (initError) throw initError;

        const url = new URL(window.location.href);
        const authError =
          url.searchParams.get("error_description") ||
          url.searchParams.get("error") ||
          url.hash.match(/(?:^|&)error_description=([^&]*)/)?.[1];

        if (authError) {
          throw new Error(decodeURIComponent(authError.replace(/\+/g, " ")));
        }

        const { data, error } = await supabase.auth.getSession();
        if (error) throw error;

        if (!data.session) {
          throw new Error(
            "لم تكتمل جلسة Google. تحقق من إعدادات OAuth وعنوان إعادة التوجيه في Supabase ثم أعد المحاولة."
          );
        }

        if (active) {
          // Remove OAuth parameters from the address bar after the session
          // has been stored, then return to the chat.
          window.history.replaceState({}, document.title, "/");
          router.replace("/");
        }
      } catch (error) {
        if (active) {
          setMessage(
            error instanceof Error
              ? error.message
              : "تعذر إكمال تسجيل الدخول باستخدام Google."
          );
        }
      }
    }

    void finishSignIn();
    return () => {
      active = false;
    };
  }, [router]);

  return (
    <main className="center">
      <div className="loader" />
      <p>{message}</p>
    </main>
  );
}
