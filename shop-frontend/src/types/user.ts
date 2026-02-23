export  interface SignupRequest {
    username: string;
    password: string;
    passwordConfirm: string;
    fullName: string;
    email: string;
}

export interface User {
    userId: number;
    username: string;
    fullName: string;
    email: string;
    authorities: string[];
}