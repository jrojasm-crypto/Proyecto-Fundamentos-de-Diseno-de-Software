package proyecto;

public class Main {
    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("  SISTEMA DE PRE-VALIDACIÓN Y MANUFACTURA ADITIVA (POO)   ");
        System.out.println("==========================================================\n");

        
        Modelo3D monoplaza = new CarroF1("Monoplaza F1 Escala",140.0, 68.0,28.5,"Equipo Ingeniería","Nivelación de base en neumáticos y soporte trasero integrado");

        System.out.println("Modelo cargado: " + monoplaza.getNombre());
        System.out.println("Dimensiones originales: " + monoplaza.getLargoMm() + " x " + monoplaza.getAnchoMm() + " mm\n");

        monoplaza.aplicarModificacionParametrica();
        
        if (monoplaza.validarRestriccionesFisicas()) {
            monoplaza.exportarSTL();
            
            if (monoplaza instanceof CarroF1) {
                ((CarroF1) monoplaza).ejecutarImpresion();
            }
        } else {
            System.out.println("\nProceso abortado: El archivo no es apto para impresión.");
        }
    }
}
