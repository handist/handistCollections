package handist.collections.dist;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public interface Serializer extends Serializable {
    void accept(ObjectOutputStream out) throws IOException;
}