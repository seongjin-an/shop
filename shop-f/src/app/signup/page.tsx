"use client";

import "@/styles/Login.modules.css";
import {useState, KeyboardEvent, useMemo, useEffect} from "react";
import {requestSignup} from "@/services/signup";


export default function LoginPage() {

    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [passwordConfirm, setPasswordConfirm] = useState<string>('');
    const [fullName, setFullName] = useState<string>('');
    const [email, setEmail] = useState<string>('');
    const [message, setMessage] = useState<string>('');

    const [idValidation, setIdValidation] = useState<boolean>(false);

    const handleError = (msg: string) => {
        setMessage(msg);
        setTimeout(() => setMessage(''), 3000)
    }

    const handleSignup = async () => {
        try {
            if (!fullName) {
                handleError("이름을 입력해주세요");
                return;
            }
            if (!username) {
                handleError("아이디를 입력해주세요.");
                return;
            }
            if (!password) {
                handleError("비밀번호를 입력해주세요.");
                return;
            }
            if (!passwordConfirm) {
                handleError("비밀번호를 확인해주세요.");
                return;
            }
            if (password !== passwordConfirm) {
                handleError("비밀번호가 일치하지 않습니다.");
                return;
            }
            if (!email) {
                handleError("이메일을 입력해주세요.");
                return;
            }
            if (!idValidation) {
                handleError("이미 사용 중인 아이디입니다.");
                return;
            }
            const res = await requestSignup({username, password, passwordConfirm, email, fullName});
            if (res) window.location.href = "/login";
            else setMessage("문제가 생겼습니다.");
        } catch (err: unknown) {
            setMessage("문제가 생겼습니다.");
            setTimeout(() => setMessage(''), 3000)
        }
    }

    const handleEnter = (event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'Enter') {
            handleSignup();
        }
    }


    function debounce<T extends (...args: any[]) => void>(
        callback: T,
        delay: number
    ) {
        let timer: NodeJS.Timeout;

        return (...args: Parameters<T>) => {
            clearTimeout(timer);
            timer = setTimeout(() => {
                callback(...args);
            }, delay);
        };
    }

    const validateUsername = async (value: string) => {
        if (!value.trim()) return;

        try {
            const res = await fetch(
                `/api/users/validate-username?username=${encodeURIComponent(value)}`,
                { method: "POST" }
            );

            if (res.status !== 200) throw new Error();

            setIdValidation(true);
            setMessage("사용 가능한 아이디입니다.");
        } catch {
            setIdValidation(false);
            setMessage("이미 사용 중인 아이디입니다.");
        }

        // 3초 후 메시지 제거
        setTimeout(() => {
            setMessage("");
        }, 3000);
    };

    const debouncedValidate = useMemo(
        () => debounce(validateUsername, 555),
        []
    );

    useEffect(() => {
        debouncedValidate(username);
    }, [username, debouncedValidate]);

    return <div className="login-wrapper">
        <div className="login-container">
            <h2>회원가입</h2>

            { message && <div className="signup-message">{message}</div>}

            <div>
                <div className="form-group">
                    <label htmlFor="username">이름</label>
                    <input type="text" name="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} />
                </div>
                <div className="form-group">
                    <label htmlFor="username">아이디</label>
                    <input type="text" name="username" value={username} onChange={(e) => setUsername(e.target.value)} />
                </div>

                <div className="form-group">
                    <label htmlFor="password">비밀번호</label>
                    <input type="password" name="password" value={password} onChange={(e) => setPassword(e.target.value)} />
                </div>
                <div className="form-group">
                    <label htmlFor="password">비밀번호 확인</label>
                    <input type="password" name="passwordConfirm" value={passwordConfirm} onChange={(e) => setPasswordConfirm(e.target.value)} />
                </div>
                <div className="form-group">
                    <label htmlFor="email">이메일</label>
                    <input type="email" name="email" value={email} onChange={(e) => setEmail(e.target.value)} onKeyUp={handleEnter} />
                </div>

                <button type="button" className="login-button" onClick={handleSignup}>회원가입</button>
            </div>
        </div>
    </div>
}