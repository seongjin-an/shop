import { SignupRequest } from "@/types/user";

export const requestSignup = async (signupRequest: SignupRequest): Promise<boolean> => {
    try {
        const res = await fetch("/gateway-api/user/api/users/signup", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(signupRequest),
        });
        return res.ok;
    } catch {
        return false;
    }
};
