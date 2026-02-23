import {create} from 'zustand/react'
import {User} from "@/types/user";

interface AuthState {
    isLoggedIn: boolean;
    user: User | null;
    initUser: (user: User) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
    isLoggedIn: false,
    user: null,
    initUser: (user: User) => set({ isLoggedIn: true, user: user })
}))