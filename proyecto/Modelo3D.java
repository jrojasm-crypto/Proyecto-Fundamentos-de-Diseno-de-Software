package proyecto;

public abstract class Modelo3D {
    private String nombre;
    private double largoMm;
    private double anchoMm;
    private double altoMm;
    private boolean mallaEstanca;

    public static final double MAX_LARGO_MM = 131.5;
    public static final double MAX_ANCHO_MM = 64.2;

    public Modelo3D(String nombre, double largoMm, double anchoMm, double altoMm) {
        this.nombre = nombre;
        this.largoMm = largoMm;
        this.anchoMm = anchoMm;
        this.altoMm = altoMm;
        this.mallaEstanca = false; 
    }

    public String getNombre(){ 
        return nombre; 
    }
    public double getLargoMm(){
        return largoMm; 
    }
    public void setLargoMm(double largoMm){
        this.largoMm = largoMm; 
    }
    
    public double getAnchoMm(){ 
        return anchoMm; 
    }
    public void setAnchoMm(double anchoMm){
        this.anchoMm = anchoMm; 
    }
    
    public double getAltoMm(){
        return altoMm; 
    }
    
    public boolean isMallaEstanca(){ 
        return mallaEstanca; 
    }
    protected void setMallaEstanca(boolean mallaEstanca){ 
        this.mallaEstanca = mallaEstanca; 
    }
    public abstract void aplicarModificacionParametrica();
    public abstract boolean validarRestriccionesFisicas();
    public abstract void exportarSTL();
}
