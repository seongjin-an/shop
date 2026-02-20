import {SignupRequest} from "@/types/user";

export const requestSignup = async (signupRequest: SignupRequest) => {
    try {
        console.log("signupRequest", signupRequest);
        const res = await fetch(`/api/users/signup`,{
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(signupRequest)
        })
        console.log(res)
        return res.ok;

    } catch (error: unknown) {
        return false;
    }
}