"use client"
import "@/styles/Main.modules.css";
import {requestUserInfo} from "@/services/api";
import {useAuthStore} from "@/store/useAuthStore";
import {useEffect} from "react";

export default function Home() {
    const {isLoggedIn, user, initUser} = useAuthStore();

    useEffect(() => {
        if (isLoggedIn || user) return;
        if (!isLoggedIn && !user) {
            requestUserInfo().then(r => {
                if (!r) {
                    window.location.href = "/login";
                    return;
                }
                initUser(r);
            })
                .catch(e => {
                    window.location.href = "/login";
                });
        }
    }, [isLoggedIn, user])

    const handleLogout = async () => {
        await fetch("/api/logout", {
            method: "POST",
            cache: "no-store",
            credentials: "include",
            headers: {
                "Content-Type": "application/json",
            }
        })
        window.location.href = "/login";
    }

    return <>
        <header>
            <div className="logo" onClick={() => console.log("user", user)}>MyBoard</div>
            <div className="user-area">
                {/*<span th:text="${#authentication.name}">username</span>*/}
                <span >{user?.fullName}</span>
                {/*<form th:action="@{/logout}" method="post">*/}
                <button className="logout-btn" type="submit" onClick={handleLogout}>로그아웃</button>
            </div>
        </header>

        <div className="container">
            <aside>
                <ul>
                    <li><a>홈</a></li>
                    <li><a>게시판</a></li>
                    <li><a>인기글</a></li>
                    <li><a>설정</a></li>
                </ul>
            </aside>

            <main>
                <div className="board-header">
                    <h2>게시판</h2>
                    <button className="write-btn">글쓰기</button>
                </div>

                <table>
                    <thead>
                    <tr>
                        <th>번호</th>
                        <th>제목</th>
                        <th>작성자</th>
                        <th>작성일</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                        <td>1</td>
                        <td>
                            <a>제목</a>
                        </td>
                        <td>작성자</td>
                        <td>2026-02-18</td>
                    </tr>
                    </tbody>
                </table>
            </main>
        </div>

        <footer>
            © 2026 MyBoard. All rights reserved.
        </footer>
    </>
}
