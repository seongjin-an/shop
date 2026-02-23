export const requestLogin = async (username?: string, password?: string) => {
    try {
        const res = await fetch(`/api/login`,{
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ username, password })
        })
        return res.ok;
    } catch (error: unknown) {
        return false;
    }
}