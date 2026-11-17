package webserver;

import db.MemoryUserRepository;
import model.User;

public class MemoryUserController {

    private static final MemoryUserController INSTANCE = new MemoryUserController();
    private MemoryUserController() {
    }

    public static MemoryUserController getInstance() {
        return INSTANCE;
    }

    public void GetSignUp(
            String userId,
            String password,
            String name,
            String email
    ){
        User user = new User(userId, password, name, email);
        MemoryUserRepository memoryUserRepository = MemoryUserRepository.getInstance();

        memoryUserRepository.addUser(user);
    }
}
