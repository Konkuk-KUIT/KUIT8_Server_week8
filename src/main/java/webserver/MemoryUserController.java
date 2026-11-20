package webserver;

import db.MemoryUserRepository;
import model.User;

public class MemoryUserController {

    private static final MemoryUserController INSTANCE = new MemoryUserController();
    MemoryUserRepository memoryUserRepository =  MemoryUserRepository.getInstance();
    private MemoryUserController() {
    }

    public static MemoryUserController getInstance() {
        return INSTANCE;
    }

    public void signup(
            String userId,
            String password,
            String name,
            String email
    ){
        User user = new User(userId, password, name, email);
        memoryUserRepository.addUser(user);
    }

    public boolean login(String userId, String password) {
        User user = memoryUserRepository.findUserById(userId);
        if(user == null || !user.getPassword().equals(password)) return false;
        return true;
    }
}
