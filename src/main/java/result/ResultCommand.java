package result;

import com.sun.jdi.request.StepRequest;

public class ResultCommand {
    private boolean succes;
    private Object donnee;
    private String message;

    public ResultCommand(boolean b, Object object, String message) {
        this.succes = b;
        this.donnee = object;
        this.message = message;
    }

    public void display() {
        System.out.println(message);
    }

    public boolean isSucces() {
        return succes;
    }

    public Object getDonnee() {
        return donnee;
    }

    @Override
    public String toString() {
        return message;
    }
}
