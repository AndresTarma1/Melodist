import com.sun.jna.Structure

/**
 * Debe ser una clase pública de nivel superior (top-level).
 * JNA usa reflexión para leer sus campos, y si la clase es privada
 * o está anidada dentro de un archivo Kotlin, el módulo com.sun.jna
 * no puede acceder a ella en Java 9+ y lanza IllegalAccessException.
 *
 * See https://stackoverflow.com/q/62240901
 */
@Structure.FieldOrder(
    "leftBorderWidth",
    "rightBorderWidth",
    "topBorderHeight",
    "bottomBorderHeight"
)
class WindowMargins(
    @JvmField var leftBorderWidth: Int = 0,
    @JvmField var rightBorderWidth: Int = 0,
    @JvmField var topBorderHeight: Int = 0,
    @JvmField var bottomBorderHeight: Int = 0
) : Structure(), Structure.ByReference