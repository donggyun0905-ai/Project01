package com.dmart.swing;

import com.dmart.dao.ItemDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.Item;

import javax.swing.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 품목명을 타이핑하면 후보가 뜨는 공용 입력칸.
 *
 * 품목이 250개가 넘어서 드롭다운을 스크롤해 고르기가 불편했기 때문에, 품목을 고르는 화면
 * (입고 등록 / 출고 등록 / 창고 간 재고 이동 / 반품·폐기)에서 모두 이걸 씁니다.
 *
 * 무한루프를 피하는 구조:
 *   예전에 JComboBox + removeAllItems()로 실시간 필터링을 만들었다가, "목록을 지우면
 *   입력칸 글자도 같이 지워지고 -> 그게 다시 이벤트를 일으키고 -> 또 지우고" 하는 무한루프가
 *   나서 앱이 멈춘 적이 있습니다. 그래서 여기서는 입력칸 자체는 프로그램이 절대 건드리지 않고,
 *   입력칸 "아래에 후보 목록 팝업만 따로" 띄웁니다. 사용자가 후보를 직접 눌렀을 때만 입력칸에
 *   글자가 들어가므로, 되먹임이 생길 수가 없는 구조입니다.
 */
public class ItemPickerField extends JTextField {

    private final ItemDao itemDao = new ItemDao();
    private final JPopupMenu popup = new JPopupMenu();
    private List<Item> options = new ArrayList<>();
    private Runnable onChange;

    public ItemPickerField() {
        super(20);
        popup.setFocusable(false); // 팝업이 포커스를 가져가면 계속 타이핑을 못 합니다

        getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
    }

    private void changed() {
        updateSuggestions();
        if (onChange != null) onChange.run();
    }

    /** 품목이 바뀌었을 때(=이름을 정확히 다 쳤거나, 지웠을 때) 할 일을 정합니다 */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** 사용 중인 품목만 DB에서 다시 읽어 후보 목록을 채웁니다.
     *  품목 관리에서 품목을 추가/비활성한 뒤에도 바로 반영되게 하려면 이걸 다시 부르면 됩니다.
     *  사용자가 입력해 둔 글자는 건드리지 않습니다. */
    public void reload() {
        List<Item> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            for (Item item : itemDao.findAll(conn)) {
                if (Boolean.TRUE.equals(item.getIsActive())) {
                    result.add(item);
                }
            }
        } catch (Exception e) {
            // 목록을 못 불러와도 화면 자체는 계속 쓸 수 있어야 하므로 조용히 넘어갑니다
            // (등록 버튼을 누르는 시점에 "등록되지 않은 품목입니다"로 걸립니다)
            e.printStackTrace();
        }
        result.sort(Comparator.comparing(Item::getItemName));
        this.options = result;
    }

    /** 바깥에서 이미 읽어둔 목록이 있으면 그걸 그대로 씁니다 (DB를 두 번 읽지 않게) */
    public void setOptions(List<Item> options) {
        List<Item> copy = new ArrayList<>(options);
        copy.sort(Comparator.comparing(Item::getItemName));
        this.options = copy;
    }

    public List<Item> getOptions() {
        return options;
    }

    /** 지금 입력된 이름과 정확히 일치하는 품목. 없으면 null (아직 다 안 쳤거나 오타) */
    public Item getSelectedItem() {
        String typed = getText().trim();
        if (typed.isEmpty()) return null;
        for (Item item : options) {
            if (item.getItemName().trim().equals(typed)) return item;
        }
        return null;
    }

    public void setSelectedItem(Item item) {
        setText(item == null ? "" : item.getItemName());
    }

    /** 목록의 첫 품목을 골라 둡니다 (예전 드롭다운이 첫 항목을 기본 선택하던 동작 유지) */
    public void selectFirstIfEmpty() {
        if (getText().trim().isEmpty() && !options.isEmpty()) {
            setSelectedItem(options.get(0));
        }
    }

    /** 입력값이 목록에 없을 때 사용자에게 보여줄 안내 문구 */
    public String notFoundMessage() {
        String typed = getText().trim();
        return typed.isEmpty()
                ? "품목명을 입력해 주세요."
                : "등록되지 않은 품목입니다: \"" + typed + "\"\n후보 목록에서 골라 주세요.";
    }

    private void updateSuggestions() {
        String typed = getText().trim();
        popup.setVisible(false);
        popup.removeAll();
        if (typed.isEmpty()) return;

        // 이미 정확히 일치하는 이름을 다 쳤으면 후보를 띄울 필요가 없습니다
        if (getSelectedItem() != null) return;

        int shown = 0;
        for (Item item : options) {
            String name = item.getItemName();
            if (name.contains(typed)) {
                JMenuItem menuItem = new JMenuItem(name);
                menuItem.addActionListener(ev -> {
                    setText(name); // 사용자가 직접 누른 경우에만 입력칸을 바꿉니다
                    popup.setVisible(false);
                });
                popup.add(menuItem);
                shown++;
                if (shown >= 10) break; // 너무 많으면 10개까지만
            }
        }
        if (shown > 0 && isShowing()) {
            popup.show(this, 0, getHeight());
        }
    }
}
