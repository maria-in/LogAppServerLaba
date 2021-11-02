package com.hannapapova.server;

import static androidx.room.OnConflictStrategy.REPLACE;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = REPLACE)
    void insertUser(UserEntity user);

    @Query("UPDATE user_table SET user_info = :userInfo WHERE user_name = :userName")
    void editUser(String userName, String userInfo);

    @Query("SELECT * FROM user_table")
    List<UserEntity> getAllUsers();

    @Query("SELECT user_id, user_name, user_password, user_info FROM user_table WHERE user_name = :userName AND user_password = :userPassword")
    UserEntity getUser(String userName, String userPassword);

    @Query("SELECT user_name FROM user_table WHERE user_name = :userName")
    String findNameUser(String userName);
}
