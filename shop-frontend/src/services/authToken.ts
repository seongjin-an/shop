import { User } from "@/types/user";

const TOKEN_KEY = "shop_token";
const REFRESH_TOKEN_KEY = "shop_refresh_token";
const USER_KEY = "shop_user";

export function setAuth(token: string, user: User, refreshToken?: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function getRefreshToken(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getToken(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): User | null {
    if (typeof window === "undefined") return null;
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try { return JSON.parse(raw) as User; } catch { return null; }
}

export function clearAuth(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
}

export function isTokenExpired(token: string): boolean {
    try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        return payload.exp * 1000 < Date.now();
    } catch {
        return true;
    }
}

export async function authFetch(url: string, options: RequestInit = {}): Promise<Response> {
    const token = getToken();
    const res = await fetch(url, {
        ...options,
        headers: {
            ...options.headers,
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
    });
    if (res.status === 401) {
        clearAuth();
        window.location.href = "/login";
    }
    return res;
}
