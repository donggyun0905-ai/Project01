package com.dmart.service;

import com.dmart.dao.AppUserDao;
import com.dmart.dao.UserWarehouseDao;
import com.dmart.dao.WarehouseDao;
import com.dmart.db.DBConnection;
import com.dmart.dto.AppUser;
import com.dmart.dto.UserWarehouse;
import com.dmart.util.PasswordUtil;

import java.sql.SQLException;
import java.util.List;

// API_명세.md 2.3~2.4 참고.
public class UserService {

    private final AppUserDao appUserDao = new AppUserDao();
    private final UserWarehouseDao userWarehouseDao = new UserWarehouseDao();
    private final WarehouseDao warehouseDao = new WarehouseDao();

    public Long create(String username, String password, String name, String role) throws SQLException {
        if (!"ADMIN".equals(role) && !"STAFF".equals(role)) {
            throw new IllegalArgumentException("role은 ADMIN 또는 STAFF여야 합니다: " + role);
        }

        return DBConnection.executeInTransactionWithResult(conn -> {
            if (appUserDao.findByUsername(conn, username) != null) {
                throw new IllegalStateException("이미 사용 중인 username입니다: " + username);
            }
            AppUser user = new AppUser();
            user.setUsername(username);
            user.setPassword(PasswordUtil.hash(password));
            user.setName(name);
            user.setRole(role);
            user.setIsActive(true);
            return appUserDao.insert(conn, user);
        });
    }

    // 2.3 참고 — PUT은 이름/비밀번호만 수정. username/role은 이 경로로 바꾸지 않는다.
    public void update(Long userId, String name, String password) throws SQLException {
        DBConnection.executeInTransaction(conn -> {
            AppUser user = appUserDao.findById(conn, userId);
            if (user == null) {
                throw new IllegalArgumentException("존재하지 않는 userId입니다: " + userId);
            }
            if (name != null) {
                user.setName(name);
            }
            if (password != null) {
                user.setPassword(PasswordUtil.hash(password));
            }
            appUserDao.update(conn, user);
        });
    }

    public void setActive(Long userId, boolean active) throws SQLException {
        DBConnection.executeInTransaction(conn -> {
            if (appUserDao.findById(conn, userId) == null) {
                throw new IllegalArgumentException("존재하지 않는 userId입니다: " + userId);
            }
            appUserDao.setActive(conn, userId, active);
        });
    }

    // 2.4 참고 — 부분 추가/삭제가 아니라 통째로 교체.
    public void replaceWarehouses(Long userId, List<Long> warehouseIds) throws SQLException {
        DBConnection.executeInTransaction(conn -> {
            if (appUserDao.findById(conn, userId) == null) {
                throw new IllegalArgumentException("존재하지 않는 userId입니다: " + userId);
            }
            for (Long warehouseId : warehouseIds) {
                if (warehouseDao.findById(conn, warehouseId) == null) {
                    throw new IllegalArgumentException("존재하지 않는 warehouseId입니다: " + warehouseId);
                }
            }
            userWarehouseDao.deleteByUserId(conn, userId);
            for (Long warehouseId : warehouseIds) {
                userWarehouseDao.insert(conn, new UserWarehouse(userId, warehouseId));
            }
        });
    }
}
