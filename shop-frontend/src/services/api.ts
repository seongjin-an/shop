
export const requestUserInfo = async () => {
    try {
        // const cookieStore = await cookies();
        // const allCookies = cookieStore.toString();
        // console.log("[cookies]", allCookies);
        // const reqUrl = `${baseUrl}/api/users/me`;
        // console.log("[requestUserInfo] reqUrl", reqUrl);
        const res = await fetch("/api/users/me",{
            cache: "no-store",
            credentials: "include",
            headers: {
                // Cookie: allCookies,
                'Content-Type': 'application/json',
            }
        })

        //console.log("[requestUserInfo] res", res)
        //if (res.status !== 200) {
        //    redirect("/login")
        //}

        // 빈 응답 방어 로직
        if (res.status === 200 && res.headers.get("content-length") === "0") {
            console.warn("로그인 세션이 만료되었거나 쿠키가 없습니다.");
            return false;
            //redirect("/login");
            }

        if (res.status !== 200) {
            return false;
            //redirect("/login");
        }

        return res.json();
    } catch (error: unknown) {
        const errorMessage = getErrorMessage(error);
        console.error(errorMessage);
    }
}

function getErrorMessage(error: unknown) {
    if (error instanceof Error) return error.message
    return String(error)
}