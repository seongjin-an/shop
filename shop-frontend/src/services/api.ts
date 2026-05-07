import { getToken, getStoredUser, isTokenExpired } from "./authToken";
import { User } from "@/types/user";

export const requestUserInfo = (): Promise<User | null> => {
    const token = getToken();
    const user = getStoredUser();
    if (!token || !user || isTokenExpired(token)) return Promise.resolve(null);
    return Promise.resolve(user);
};
