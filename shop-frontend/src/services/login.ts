import { setAuth } from "./authToken";
import { User } from "@/types/user";

interface LoginResponse {
    accessToken: string;
    refreshToken: string;
    user: User;
}

export const requestLogin = async (username?: string, password?: string): Promise<boolean> => {
    try {
        const res = await fetch("/gateway-api/user/api/users/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password }),
        });
        if (!res.ok) return false;
        const data: LoginResponse = await res.json();
        setAuth(data.accessToken, data.user, data.refreshToken);
        return true;
    } catch {
        return false;
    }
};
