package com.dmart.swing;

// MainFrame 위쪽 "새로고침" 버튼 하나로, 지금 보이는 화면의 데이터를 다시 불러오게 하는 표시.
// 화면마다 있던 개별 새로고침 버튼들을 없애는 대신 이 인터페이스를 구현한다.
public interface Refreshable {
    void refreshAll();
}
