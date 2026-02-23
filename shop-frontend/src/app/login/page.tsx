"use client";

import "@/styles/Login.modules.css";
import {useState, KeyboardEvent} from "react";
import {requestLogin} from "@/services/login";


export default function LoginPage() {

    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [error, setError] = useState<string>('');

    const handleLogin = async () => {
        try {
            if (!username) {
                setError("아이디를 입력해주세요");
                setTimeout(() => setError(''), 3000)
                return;
            }
            if (!password) {
                setError("비밀번호를 입력해주세요.");
                setTimeout(() => setError(''), 3000)
                return;
            }
            const res = await requestLogin(username, password);
            if (res) window.location.href = "/";
            else setError("문제가 생겼습니다.");
        } catch (err: unknown) {
            setError("문제가 생겼습니다.");
            setTimeout(() => setError(''), 3000)
        }
    }

    const handleEnter = (event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'Enter') {
            handleLogin();
        }
    }


    return <div className="login-wrapper">
        <div className="login-container">
            <h2>로그인</h2>

            { error && <div className="error-message">{error}</div>}

            <div>
                <div className="form-group">
                    <label htmlFor="username">아이디</label>
                    <input type="text" id="username" name="username" value={username}
                           onChange={(e) => setUsername(e.target.value)}
                           onKeyUp={handleEnter}/>
                </div>

                <div className="form-group">
                    <label htmlFor="password">비밀번호</label>
                    <input type="password" id="password" name="password" value={password}
                           onChange={(e) => setPassword(e.target.value)}
                           onKeyUp={handleEnter}/>
                </div>

                <button type="button" className="login-button" onClick={handleLogin}>로그인</button>
            </div>
            {/* /signup */}
            <form method="get">
                <div className="footer-text">
                    아직 회원이 아니신가요? <a href="/signup">회원가입</a>
                </div>
            </form>
        </div>
    </div>
}