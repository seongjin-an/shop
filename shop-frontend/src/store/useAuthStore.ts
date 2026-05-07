import { create } from "zustand/react";
import { User } from "@/types/user";
import { clearAuth } from "@/services/authToken";

interface AuthState {
    isLoggedIn: boolean;
    user: User | null;
    initUser: (user: User) => void;
    logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
    isLoggedIn: false,
    user: null,
    initUser: (user: User) => set({ isLoggedIn: true, user }),
    logout: () => {
        clearAuth();
        set({ isLoggedIn: false, user: null });
    },
}));
