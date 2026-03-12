
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author alanc
 */
public class TablaSimbolos {
    // REPRESENTA UN SIMBOLO (VARIABLE) EN LA TABLA
    static class Simbolo {
        String nombre;      // NOMBRE DE LA VARIABLE
        String tipo;        // TIPO: INT, FLOAT, STRING, BOOL
        int linea;          // LINEA DONDE FUE DECLARADA
        String direccion;   // DIRECCION DE MEMORIA SIMULADA
        boolean usada;      // SI FUE USADA O NO

        Simbolo(String nombre, String tipo, int linea, String direccion) {
            this.nombre = nombre;
            this.tipo = tipo;
            this.linea = linea;
            this.direccion = direccion;
            this.usada = false;
        }
    }

    // ALMACEN PRINCIPAL
    Map<String, Simbolo> simbolos = new LinkedHashMap<>();
    int contadorDireccion = 1000; // DIRECCION BASE SIMULADA

    // AGREGA UNA VARIABLE A LA TABLA
    void agregar(String nombre, String tipo, int linea) {
        if (!simbolos.containsKey(nombre)) {
            String dir = "0x" + Integer.toHexString(contadorDireccion);
            simbolos.put(nombre, new Simbolo(nombre, tipo, linea, dir));
            contadorDireccion += 4;
        }
    }

    // MARCA UNA VARIABLE COMO USADA
    void marcarUsada(String nombre) {
        if (simbolos.containsKey(nombre)) {
            simbolos.get(nombre).usada = true;
        }
    }

    // VERIFICA SI EXISTE
    boolean existe(String nombre) {
        return simbolos.containsKey(nombre);
    }

    // OBTIENE EL TIPO DE UNA VARIABLE
    String getTipo(String nombre) {
        return simbolos.containsKey(nombre) ? simbolos.get(nombre).tipo : null;
    }

}
