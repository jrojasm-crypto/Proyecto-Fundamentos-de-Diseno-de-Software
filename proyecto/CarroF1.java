package proyecto;

public class CarroF1 extends Modelo3D {
    private String escuderia;
    private String detalleModificacion;

    public CarroF1(String nombre, double largoMm, double anchoMm, double altoMm, String escuderia, String detalleModificacion) {
        super(nombre, largoMm, anchoMm, altoMm);
        this.escuderia = escuderia;
        this.detalleModificacion = detalleModificacion;
    }

    @Override
    public void aplicarModificacionParametrica() {
        System.out.println("[Paso 1 - Modificación] Aplicando ajuste geométrico al modelo: " + detalleModificacion);
        if (getLargoMm() > MAX_LARGO_MM || getAnchoMm() > MAX_ANCHO_MM) {
            System.out.println("   -> Ajustando escala automáticamente para cumplir límite compacto...");
            setLargoMm(115.0);
            setAnchoMm(52.0);
        }
        setMallaEstanca(true); 
        System.out.println("   -> Geometría cerrada (malla estanca) verificada correctamente.");
    }

    @Override
    public boolean validarRestriccionesFisicas() {
        System.out.println("[Paso 2 - Pre-validación] Verificando límites (Máx: " + MAX_LARGO_MM + " x " + MAX_ANCHO_MM + " mm)...");
        boolean cumpleDimensiones = (getLargoMm() <= MAX_LARGO_MM) && (getAnchoMm() <= MAX_ANCHO_MM);
        
        if (cumpleDimensiones && isMallaEstanca()) {
            System.out.println("   -> [APROBADO] Dimensiones actuales: " + getLargoMm() + " x " + getAnchoMm() + " x " + getAltoMm() + " mm.");
            return true;
        } else {
            System.out.println("   -> [ERROR] El modelo no cumple con las restricciones físicas.");
            return false;
        }
    }

    @Override
    public void exportarSTL() {
        System.out.println("[Paso 3 - Exportación] Generando archivo '" + getNombre().replace(" ", "_") + "_modificado.stl'...");
        System.out.println("   -> Archivo STL exportado y listo para laminado (Slicing).");
    }

    public void ejecutarImpresion() {
        System.out.println("\n[Paso 4 - Manufactura Aditiva] Enviando archivo a la impresora 3D...");
        System.out.println("   -> Preparando cama caliente y extrusor...");
        System.out.println("   -> Realizando impresión de monoplaza (" + escuderia + ")...");
        
        for (int progreso = 25; progreso <= 100; progreso += 25) {
            System.out.println("      * Avance de impresión: " + progreso + "%");
        }
        
        System.out.println("   -> Impresión finalizada. Pieza lista para retirar de la cama.");
    }
}
