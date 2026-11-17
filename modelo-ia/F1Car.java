import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.stream.IntStream;

/**
 * Monoplaza generico tipo Formula 1 (reglamento 2026) a escala 1:43 -> archivo STL binario.
 *
 * Sin dependencias: solo el JDK (Java 8 o superior).
 *
 *   Compilar:  javac F1Car.java
 *   Ejecutar:  java F1Car                      (usa los valores por defecto)
 *              java F1Car 0.25 0.03 salida.stl (resolucion mm, tolerancia mm, nombre)
 *
 * COMO FUNCIONA
 *   1. distancia(x, y, z) describe el carro como un "campo de distancia": devuelve un numero
 *      negativo dentro del solido y positivo fuera. Cada pieza es una funcion sencilla
 *      (capsula, caja, elipsoide, cuerpo por secciones...) y las piezas se unen con min().
 *   2. Ese campo se muestrea en una rejilla 3D y "marching cubes" saca la superficie.
 *      El resultado es UNA sola malla cerrada (estanca), lista para imprimir.
 *   3. Se simplifica la malla (menos triangulos donde la superficie es plana) y se escribe el STL.
 *
 * PARA EDITAR EL CARRO: casi todo esta en las secciones 1 (medidas) y 2 (piezas).
 *
 * Ejes y unidades: milimetros a escala del modelo.
 *   x = largo (0 = punta del aleron delantero, crece hacia atras)
 *   y = ancho (el carro es simetrico: solo se modela el lado y >= 0)
 *   z = alto  (0 = suelo)
 */
public class F1Car {

    // =====================================================================================
    // 1. MEDIDAS PRINCIPALES
    // =====================================================================================
    static final double ESCALA = 43.0;                         // 1:43
    static final double BATALLA = 3400 / ESCALA;               // distancia entre ejes (max. 2026: 3400 mm)
    static final double MEDIO_ANCHO = 1900 / ESCALA / 2;       // ancho total 2026: 1900 mm
    static final double X_EJE_DEL = 26.5;                      // posicion del eje delantero
    static final double X_EJE_TRAS = X_EJE_DEL + BATALLA;      // posicion del eje trasero
    static final double R_DEL = 705 / ESCALA / 2;              // radio neumatico delantero
    static final double ANCHO_DEL = 280 / ESCALA;              // ancho neumatico delantero
    static final double R_TRAS = 710 / ESCALA / 2;             // radio neumatico trasero
    static final double ANCHO_TRAS = 375 / ESCALA;             // ancho neumatico trasero
    static final double Y_DEL = MEDIO_ANCHO - ANCHO_DEL / 2;   // centro de la rueda delantera
    static final double Y_TRAS = MEDIO_ANCHO - ANCHO_TRAS / 2; // centro de la rueda trasera
    static final double Z_APOYO = 0.2;                         // aplana la huella de los neumaticos

    // Calidad de la malla
    static final double RESOLUCION = 0.25;   // tamano de celda de la rejilla en mm (menor = mas detalle y mas lento)
    static final double TOLERANCIA = 0.03;   // error maximo de la simplificacion en mm (0 = no simplificar)
    static final String ARCHIVO = "f1_2026_escala_1-43.stl";

    // Caja que contiene al modelo (mm). Si agrandas el carro, agranda tambien estos limites.
    static final double X_MIN = -1.2, X_MAX = 124.8, Y_MAX = 23.4, Z_MIN = -0.8, Z_MAX = 23.6;

    // =====================================================================================
    // 2. PIEZAS DEL CARRO
    // =====================================================================================

    // Morro + monocasco + cubierta inferior del motor.
    // Cada columna es una seccion transversal: posicion x, techo, piso y medio ancho.
    static final double[] CX = {1.5, 6, 12, 20, 26.5, 34, 42, 47, 53, 60, 66, 75, 85, 95, 104, 110, 114};
    static final Cuerpo CUERPO = new Cuerpo(CX,
            neg(new double[]{1.7, 2.4, 3.2, 4.3, 5.0, 5.9, 7.0, 7.5, 7.6, 7.4, 7.0, 6.2, 4.8, 3.4, 2.5, 1.9, 1.5}),
            new double[]{1.7, 2.4, 3.2, 4.3, 5.0, 5.9, 7.0, 7.5, 7.6, 7.4, 7.0, 6.2, 4.8, 3.4, 2.5, 1.9, 1.5},
            new double[]{2.0, 2.2, 2.8, 3.6, 4.0, 3.0, 1.6, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.9, 3.5},
            new double[]{3.5, 5.0, 6.9, 9.8, 11.9, 12.9, 13.3, 13.3, 13.2, 13.8, 14.6, 13.6, 11.4, 9.0, 7.2, 6.2, 5.6},
            3.0, 0.6);

    // Pontones laterales (van de y = 3 hasta el borde exterior).
    static final double[] PX = {47, 50, 56, 64, 72, 80, 88, 95, 99};
    static final Cuerpo PONTON = new Cuerpo(PX,
            new double[]{3, 3, 3, 3, 3, 3, 3, 3, 3},
            new double[]{17.0, 17.6, 17.8, 17.3, 16.0, 14.0, 11.5, 8.5, 6.5},
            new double[]{6.0, 4.8, 3.2, 1.6, 1.2, 1.2, 1.2, 1.2, 1.2},
            new double[]{11.6, 11.8, 11.6, 10.6, 9.0, 7.2, 5.4, 3.8, 3.0},
            3.5, 0.4);

    // Toma de admision sobre el casco + espina de la cubierta motor.
    static final double[] SX = {60.5, 63, 70, 80, 90, 100, 108, 110.5};
    static final Cuerpo ESPINA = new Cuerpo(SX,
            neg(new double[]{2.6, 2.9, 2.9, 2.5, 1.9, 1.3, 1.0, 0.9}),
            new double[]{2.6, 2.9, 2.9, 2.5, 1.9, 1.3, 1.0, 0.9},
            new double[]{4, 4, 4, 4, 4, 4, 4, 4},
            new double[]{21.6, 22.0, 20.8, 17.5, 13.8, 10.2, 7.4, 6.6},
            2.4, 0.3);

    // Fondo plano: medio ancho en funcion de x.
    static final Perfil FONDO = new Perfil(
            new double[]{33, 35, 38, 42, 60, 92, 95.3, 96.3, 105, 115.5},
            new double[]{7, 11, 16.5, 18.3, 18.3, 17.8, 17.5, 12.6, 12.2, 12.2});

    // Barras: halo, soportes de retrovisor y suspension. Cada una es {x0,y0,z0, x1,y1,z1, radio}.
    static final double[][] BARRAS = barras();

    static double[][] barras() {
        java.util.List<double[]> b = new java.util.ArrayList<>();
        // --- halo: arco de 13 tramos + apoyo trasero + pilar central
        double rHalo = 0.7;
        double[][] p = new double[13][];
        for (int i = 0; i < 13; i++) {
            double t = Math.toRadians(90.0 * i / 12);
            p[i] = new double[]{60.5 - 15.0 * Math.cos(t), 5.2 * Math.sin(t), 15.1 - 0.3 * Math.cos(t)};
        }
        for (int i = 0; i < 12; i++) b.add(barra(p[i], p[i + 1], rHalo));
        b.add(barra(p[12], new double[]{63.5, 5.2, 12.0}, rHalo));
        b.add(barra(p[0], new double[]{43.2, 0, 12.6}, rHalo + 0.1));
        // --- soportes de los retrovisores
        b.add(new double[]{46.5, 6.3, 12.5, 46.5, 10.2, 13.7, 0.5});
        b.add(new double[]{47.6, 10.6, 11.2, 46.6, 10.6, 13.6, 0.45});
        // --- suspension delantera
        double r = 0.6;
        double yd = MEDIO_ANCHO - ANCHO_DEL - 0.4;          // punto de anclaje en la rueda
        double xf = X_EJE_DEL;
        b.add(new double[]{xf, yd, 11.3, 22.5, 2, 9.0, r});   // triangulo superior
        b.add(new double[]{xf, yd, 11.3, 33.5, 2, 11.2, r});
        b.add(new double[]{xf, yd, 5.0, 23.0, 2, 4.9, r});    // triangulo inferior
        b.add(new double[]{xf, yd, 5.0, 34.0, 2, 4.2, r});
        b.add(new double[]{xf, yd, 5.6, 28.5, 2, 10.6, r});   // push-rod
        b.add(new double[]{xf - 2.2, yd, 9.6, 22.0, 2, 8.0, r}); // bieleta de direccion
        // --- suspension trasera
        double yt = MEDIO_ANCHO - ANCHO_TRAS - 0.4;
        double xr = X_EJE_TRAS;
        b.add(new double[]{xr, yt, 11.6, 96.0, 0.5, 7.0, r});
        b.add(new double[]{xr, yt, 11.6, 107.5, 0.5, 5.8, r});
        b.add(new double[]{xr, yt, 4.9, 97.0, 0.5, 3.4, r});
        b.add(new double[]{xr, yt, 4.9, 109.0, 0.5, 4.6, r});
        b.add(new double[]{xr, yt, R_TRAS, xr, 0.5, 6.0, r});  // palier
        return b.toArray(new double[0][]);
    }

    /** Campo de distancia del carro completo: negativo = dentro del solido. */
    static double distancia(double x, double y, double z) {
        y = Math.abs(y);                                    // simetria izquierda/derecha

        // ---- carroceria
        double cuerpo = CUERPO.d(x, y, z);
        double ponton = PONTON.d(x, y, z);
        ponton = Math.max(ponton, -elipsoide(x, y, z, 47.0, 12.0, 9.5, 1.6, 3.9, 1.2, 2.5));   // boca del ponton
        cuerpo = unionSuave(cuerpo, ponton, 1.5);
        cuerpo = Math.max(cuerpo, -elipsoide(x, y, z, 53.8, 0, 14.8, 6.9, 3.9, 4.3, 4.0));     // hueco del cockpit
        double espina = ESPINA.d(x, y, z);
        espina = Math.max(espina, -elipsoide(x, y, z, 60.4, 0, 19.7, 1.6, 1.7, 1.5, 2.0));     // boca de la admision
        cuerpo = unionSuave(cuerpo, espina, 2.2);
        cuerpo = Math.min(cuerpo, elipsoide(x, y, z, 56.2, 0, 11.6, 3.2, 3.5, 1.8, 2.0));      // hombros del piloto
        cuerpo = Math.min(cuerpo, elipsoide(x, y, z, 55.6, 0, 14.6, 3.1, 2.9, 3.0, 2.0));      // casco

        // ---- fondo plano y difusor (el fondo sube desde x = 98)
        double zFondo = 1.35 + 2.3 * limitar((x - 98.0) / 17.5, 0, 1);
        double fondo = Math.max(y - FONDO.en(x), Math.abs(z - zFondo) - 0.55);
        fondo = Math.max(fondo, Math.max(33 - x, x - 115.5));
        double f = Math.min(cuerpo, fondo);

        // ---- halo, soportes y suspension
        for (double[] b : BARRAS) f = Math.min(f, capsula(x, y, z, b));

        // ---- retrovisores
        f = Math.min(f, elipsoide(x, y, z, 46.5, 10.2, 14.2, 0.8, 1.6, 1.0, 2.0));

        // ---- aleron delantero: 3 planos + deriva lateral
        double flecha = 0.17;
        f = Math.min(f, ala(x, y, z, 1.0, flecha, 1.9, 6, 6.2, 1.0, 21.6));
        f = Math.min(f, ala(x, y, z, 6.2, flecha, 2.75, 20, 4.6, 1.0, 21.6));
        f = Math.min(f, ala(x, y, z, 9.9, flecha, 4.2, 30, 3.6, 1.0, 21.6));
        double deriva = caja(x, y, z, 4.2, 17.0, MEDIO_ANCHO - 0.9, MEDIO_ANCHO, 0.8, 30);
        deriva = Math.max(deriva, z - (3.6 + 0.25 * (x - 4.2)));
        f = Math.min(f, deriva);

        // ---- aleron trasero: 2 planos + derivas + pilon central
        f = Math.min(f, ala(x, y, z, 110.2, 0, 17.3, 10, 7.6, 1.0, 11.8));
        f = Math.min(f, ala(x, y, z, 116.8, 0, 18.7, 27, 6.6, 1.0, 11.8));
        deriva = caja(x, y, z, 110.2, 123.2, 11.3, 12.2, 2.6, 22.0);
        deriva = Math.max(deriva, (2.6 + 1.0 * (x - 115.0)) - z);
        f = Math.min(f, deriva);
        f = Math.min(f, caja(x, y, z, 109.0, 114.0, -1, 0.6, 5.0, 17.8));

        // ---- ruedas y manguetas
        f = Math.min(f, neumatico(x, y, z, X_EJE_DEL, Y_DEL, R_DEL, R_DEL, ANCHO_DEL));
        f = Math.min(f, neumatico(x, y, z, X_EJE_TRAS, Y_TRAS, R_TRAS, R_TRAS, ANCHO_TRAS));
        double yi = MEDIO_ANCHO - ANCHO_DEL;                // cara interior del neumatico delantero
        f = Math.min(f, cilindroY(x, y, z, X_EJE_DEL, R_DEL, 4.0, yi - 1.1, yi + 0.6));
        yi = MEDIO_ANCHO - ANCHO_TRAS;
        f = Math.min(f, cilindroY(x, y, z, X_EJE_TRAS, R_TRAS, 4.0, yi - 0.9, yi + 0.6));

        // ---- plano de apoyo: corta todo lo que quede por debajo
        return Math.max(f, Z_APOYO - z);
    }

    // =====================================================================================
    // 3. FORMAS BASICAS (cada una devuelve la distancia a su superficie)
    // =====================================================================================

    static double limitar(double v, double a, double b) { return v < a ? a : (v > b ? b : v); }

    /** Union con un pequeno radio de acuerdo k entre las dos piezas. */
    static double unionSuave(double a, double b, double k) {
        double t = limitar(0.5 + 0.5 * (b - a) / k, 0, 1);
        return b * (1 - t) + a * t - k * t * (1 - t);
    }

    static double interseccionSuave(double a, double b, double k) { return -unionSuave(-a, -b, k); }

    static double[] neg(double[] v) {
        double[] r = new double[v.length];
        for (int i = 0; i < v.length; i++) r[i] = -v[i];
        return r;
    }

    static double[] barra(double[] a, double[] b, double r) {
        return new double[]{a[0], a[1], a[2], b[0], b[1], b[2], r};
    }

    /** Barra redondeada entre dos puntos. */
    static double capsula(double x, double y, double z, double[] c) {
        double bx = c[3] - c[0], by = c[4] - c[1], bz = c[5] - c[2];
        double t = ((x - c[0]) * bx + (y - c[1]) * by + (z - c[2]) * bz) / (bx * bx + by * by + bz * bz);
        t = limitar(t, 0, 1);
        double dx = x - (c[0] + bx * t), dy = y - (c[1] + by * t), dz = z - (c[2] + bz * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - c[6];
    }

    static double caja(double x, double y, double z, double x0, double x1, double y0, double y1, double z0, double z1) {
        double d = Math.max(x0 - x, x - x1);
        d = Math.max(d, Math.max(y0 - y, y - y1));
        return Math.max(d, Math.max(z0 - z, z - z1));
    }

    /** Elipsoide de centro (cx,cy,cz) y semiejes (sx,sy,sz). p = 2 es redondo; p mayor lo hace mas "cuadrado". */
    static double elipsoide(double x, double y, double z, double cx, double cy, double cz,
                            double sx, double sy, double sz, double p) {
        double ax = Math.abs((x - cx) / sx), ay = Math.abs((y - cy) / sy), az = Math.abs((z - cz) / sz);
        double m = Math.min(sx, Math.min(sy, sz));
        double q = Math.max(ax, Math.max(ay, az));
        if (q > 2.5) return (q - 1.0) * m;                  // lejos: basta una cota (ahorra tiempo)
        double d = p == 2.0 ? Math.sqrt(ax * ax + ay * ay + az * az)
                : Math.pow(Math.pow(ax, p) + Math.pow(ay, p) + Math.pow(az, p), 1.0 / p);
        return (d - 1.0) * m;
    }

    /**
     * Plano de aleron: losa inclinada "grados", con borde de ataque en (xBorde, z0), cuerda y espesor
     * dados, que va del centro del carro hasta y = yFin. "flecha" retrasa el borde hacia los extremos.
     */
    static double ala(double x, double y, double z, double xBorde, double flecha, double z0,
                      double grados, double cuerda, double espesor, double yFin) {
        double c = Math.cos(Math.toRadians(grados)), s = Math.sin(Math.toRadians(grados));
        double dx = x - (xBorde + flecha * y), dz = z - z0;
        double u = dx * c + dz * s, v = -dx * s + dz * c;
        double d = Math.max(Math.abs(v) - espesor / 2, Math.max(-u, u - cuerda));
        return Math.max(d, y - yFin);
    }

    /** Neumatico con hombros redondeados, rebaje de llanta por ambas caras y tuerca central. */
    static double neumatico(double x, double y, double z, double cx, double cy, double cz, double radio, double ancho) {
        double rr = 1.3;                                    // radio del hombro
        double rad = Math.hypot(x - cx, z - cz);
        double ay = Math.abs(y - cy);
        double q1 = rad - (radio - rr), q2 = ay - (ancho / 2 - rr);
        double d = Math.min(Math.max(q1, q2), 0) + Math.hypot(Math.max(q1, 0), Math.max(q2, 0)) - rr;
        double llanta = Math.max(rad - 5.2, (ancho / 2 - 0.55) - ay);
        d = Math.max(d, -llanta);
        double tuerca = Math.max(rad - 1.0, ay - (ancho / 2 - 0.1));
        return Math.min(d, tuerca);
    }

    /** Cilindro con eje paralelo a y. */
    static double cilindroY(double x, double y, double z, double cx, double cz, double r, double y0, double y1) {
        double rad = Math.hypot(x - cx, z - cz);
        return Math.max(rad - r, Math.max(y0 - y, y - y1));
    }

    /**
     * Curva suave que pasa por los puntos (x[i], y[i]) sin oscilar entre ellos
     * (interpolacion cubica monotona, PCHIP). Se tabula una vez para evaluarla rapido.
     */
    static final class Perfil {
        static final double PASO = 0.01, MARGEN = 4.0;
        final double x0;
        final double[] tabla;

        Perfil(double[] x, double[] y) {
            int n = x.length;
            double[] h = new double[n - 1], d = new double[n - 1], m = new double[n];
            for (int i = 0; i < n - 1; i++) { h[i] = x[i + 1] - x[i]; d[i] = (y[i + 1] - y[i]) / h[i]; }
            for (int i = 1; i < n - 1; i++) {
                if (d[i - 1] * d[i] <= 0) { m[i] = 0; continue; }
                double w1 = 2 * h[i] + h[i - 1], w2 = h[i] + 2 * h[i - 1];
                m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i]);
            }
            m[0] = extremo(h[0], h[1], d[0], d[1]);
            m[n - 1] = extremo(h[n - 2], h[n - 3], d[n - 2], d[n - 3]);
            x0 = x[0] - MARGEN;
            int nt = (int) Math.ceil((x[n - 1] + MARGEN - x0) / PASO) + 2;
            tabla = new double[nt];
            int k = 0;
            for (int i = 0; i < nt; i++) {
                double t = x0 + i * PASO;
                while (k < n - 2 && t > x[k + 1]) k++;
                double s = (t - x[k]) / h[k], s2 = s * s, s3 = s2 * s;
                tabla[i] = y[k] * (2 * s3 - 3 * s2 + 1) + h[k] * m[k] * (s3 - 2 * s2 + s)
                        + y[k + 1] * (-2 * s3 + 3 * s2) + h[k] * m[k + 1] * (s3 - s2);
            }
        }

        static double extremo(double h0, double h1, double d0, double d1) {
            double v = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1);
            if (Math.signum(v) != Math.signum(d0)) return 0;
            if (Math.signum(d0) != Math.signum(d1) && Math.abs(v) > 3 * Math.abs(d0)) return 3 * d0;
            return v;
        }

        double en(double x) {
            double u = limitar((x - x0) / PASO, 0, tabla.length - 1.001);
            int i = (int) u;
            double fr = u - i;
            return tabla[i] * (1 - fr) + tabla[i + 1] * fr;
        }
    }

    /**
     * Cuerpo "por secciones": en cada posicion x la seccion transversal es una superelipse que va
     * de yInt a yExt (ancho) y de piso a techo (alto). "p" controla lo cuadrada que es la seccion
     * (2 = elipse, 4 = casi rectangulo) y "tapa" redondea los extremos delantero y trasero.
     */
    static final class Cuerpo {
        final Perfil yInt, yExt, piso, techo;
        final double p, tapa, xIni, xFin;

        Cuerpo(double[] x, double[] yInt, double[] yExt, double[] piso, double[] techo, double p, double tapa) {
            this.yInt = new Perfil(x, yInt);
            this.yExt = new Perfil(x, yExt);
            this.piso = new Perfil(x, piso);
            this.techo = new Perfil(x, techo);
            this.p = p;
            this.tapa = tapa;
            this.xIni = x[0];
            this.xFin = x[x.length - 1];
        }

        double d(double x, double y, double z) {
            double fuera = Math.max(xIni - x, x - xFin);
            if (fuera > 3.0) return fuera;                  // lejos de los extremos: basta una cota
            double yi = yInt.en(x), yo = yExt.en(x), zb = piso.en(x), zt = techo.en(x);
            double a = Math.max((yo - yi) / 2, 0.05), b = Math.max((zt - zb) / 2, 0.05);
            double ay = Math.abs(y - (yo + yi) / 2) / a, az = Math.abs(z - (zt + zb) / 2) / b;
            double d = (Math.pow(Math.pow(ay, p) + Math.pow(az, p), 1.0 / p) - 1.0) * Math.min(a, b);
            d = interseccionSuave(d, xIni - x, tapa);
            return interseccionSuave(d, x - xFin, tapa);
        }
    }

    // =====================================================================================
    // 4. PROGRAMA PRINCIPAL
    // =====================================================================================
    public static void main(String[] args) throws IOException {
        double h = args.length > 0 ? Double.parseDouble(args[0]) : RESOLUCION;
        double tol = args.length > 1 ? Double.parseDouble(args[1]) : TOLERANCIA;
        String salida = args.length > 2 ? args[2] : ARCHIVO;
        long t0 = System.currentTimeMillis();

        // --- rejilla (con una capa extra de "vacio" alrededor para que la malla cierre)
        int nxr = (int) Math.round((X_MAX - X_MIN) / h) + 1;
        int nyh = (int) Math.round(Y_MAX / h) + 1;          // nodos del lado y >= 0 (incluye y = 0)
        int nzr = (int) Math.round((Z_MAX - Z_MIN) / h) + 1;
        final int NX = nxr + 2, NY = 2 * nyh - 1 + 2, NZ = nzr + 2;
        final float[] g = new float[NX * NY * NZ];
        Arrays.fill(g, 1.0f);
        final double ox = X_MIN - h, oy = -nyh * h, oz = Z_MIN - h;   // posicion del nodo (0,0,0)
        System.out.printf("Rejilla %d x %d x %d (celda %.2f mm). Calculando el campo...%n", NX, NY, NZ, h);
        IntStream.range(1, NX - 1).parallel().forEach(i -> {
            double x = ox + i * h;
            for (int j = nyh; j < NY - 1; j++) {            // solo y >= 0; el otro lado se copia
                double y = oy + j * h;
                int espejo = 2 * nyh - j;
                for (int k = 1; k < NZ - 1; k++) {
                    float v = (float) distancia(x, y, oz + k * h);
                    g[(i * NY + j) * NZ + k] = v;
                    g[(i * NY + espejo) * NZ + k] = v;
                }
            }
        });

        quitarPiezasSueltas(g, NX, NY, NZ, (float) h);

        System.out.println("Extrayendo la superficie...");
        Malla m = marchingCubes(g, NX, NY, NZ, h, ox, oy, oz);
        System.out.printf("  %d triangulos%n", m.nf);
        if (tol > 0) {
            System.out.println("Simplificando...");
            simplificar(m, tol);
            System.out.printf("  %d triangulos%n", m.nf);
        }

        // --- x = 0 en la punta delantera y z = 0 en el plano de apoyo
        double[] lim = m.limites();
        for (int i = 0; i < m.nv; i++) { m.v[3 * i] -= lim[0]; m.v[3 * i + 2] -= lim[2]; }

        escribirStl(m, salida);
        verificar(m);
        System.out.printf("Listo: %s  (%.1f s)%n", salida, (System.currentTimeMillis() - t0) / 1000.0);
    }

    // =====================================================================================
    // 5. DE AQUI EN ADELANTE: MAQUINARIA (normalmente no hace falta tocarla)
    // =====================================================================================

    /** Malla de triangulos indexada: v = xyz de cada vertice, f = 3 indices por triangulo. */
    static final class Malla {
        double[] v = new double[3 << 16];
        int[] f = new int[3 << 16];
        int nv, nf;

        int vertice(double x, double y, double z) {
            if (3 * nv + 3 > v.length) v = Arrays.copyOf(v, v.length * 2);
            v[3 * nv] = x; v[3 * nv + 1] = y; v[3 * nv + 2] = z;
            return nv++;
        }

        void triangulo(int a, int b, int c) {
            if (3 * nf + 3 > f.length) f = Arrays.copyOf(f, f.length * 2);
            f[3 * nf] = a; f[3 * nf + 1] = b; f[3 * nf + 2] = c;
            nf++;
        }

        double[] limites() {
            double[] r = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
            for (int i = 0; i < nv; i++)
                for (int k = 0; k < 3; k++) {
                    r[k] = Math.min(r[k], v[3 * i + k]);
                    r[3 + k] = Math.max(r[3 + k], v[3 * i + k]);
                }
            return r;
        }
    }

    /** Deja solo el bloque solido mas grande (elimina migajas que no esten unidas al carro). */
    static void quitarPiezasSueltas(float[] g, int NX, int NY, int NZ, float relleno) {
        int[] etiqueta = new int[g.length];
        int[] pila = new int[g.length];
        int[] paso = {NY * NZ, -NY * NZ, NZ, -NZ, 1, -1};
        int n = 0, mejor = 0;
        long mejorTam = 0;
        java.util.List<Long> tam = new java.util.ArrayList<>();
        for (int s = 0; s < g.length; s++) {
            if (g[s] >= 0 || etiqueta[s] != 0) continue;
            n++;
            long cuenta = 0;
            int tope = 0;
            pila[tope++] = s;
            etiqueta[s] = n;
            while (tope > 0) {
                int c = pila[--tope];
                cuenta++;
                for (int d : paso) {
                    int q = c + d;                          // el borde de la rejilla es vacio: no hay desbordes
                    if (g[q] < 0 && etiqueta[q] == 0) { etiqueta[q] = n; pila[tope++] = q; }
                }
            }
            tam.add(cuenta);
            if (cuenta > mejorTam) { mejorTam = cuenta; mejor = n; }
        }
        if (n > 1) {
            for (int s = 0; s < g.length; s++) if (etiqueta[s] != 0 && etiqueta[s] != mejor) g[s] = relleno;
            System.out.printf("  piezas sueltas eliminadas: %d (celdas: %s)%n", n - 1, tam);
        }
    }

    // ---- Marching cubes -----------------------------------------------------------------
    // La tabla de casos se genera sola (no esta copiada a mano): para cada una de las 256
    // combinaciones dentro/fuera de las 8 esquinas de un cubo se unen los cortes de las aristas
    // cara por cara. Como la regla de cada cara solo depende de esa cara, dos cubos vecinos
    // siempre coinciden y la malla sale cerrada.
    static final int[] E0 = new int[12], E1 = new int[12], EJE = new int[12];
    static final int[][] ARISTA = new int[8][8];
    static final int[][][] CASOS = new int[256][][];

    static {
        int n = 0;
        for (int a = 0; a < 3; a++)
            for (int c = 0; c < 8; c++)
                if ((c >> a & 1) == 0) {
                    E0[n] = c; E1[n] = c | (1 << a); EJE[n] = a;
                    ARISTA[c][c | (1 << a)] = n; ARISTA[c | (1 << a)][c] = n;
                    n++;
                }
        for (int m = 0; m < 256; m++) CASOS[m] = lazos(m);
    }

    static int[][] lazos(int mascara) {
        int[][] vec = new int[12][2];
        int[] cnt = new int[12];
        for (int a = 0; a < 3; a++)
            for (int s = 0; s < 2; s++) {
                int b = (a + 1) % 3, c = (a + 2) % 3;
                int[] q = {s << a, s << a | 1 << b, s << a | 1 << b | 1 << c, s << a | 1 << c};
                boolean[] in = new boolean[4];
                int[] e = new int[4];
                for (int i = 0; i < 4; i++) { in[i] = (mascara >> q[i] & 1) == 1; e[i] = ARISTA[q[i]][q[(i + 1) % 4]]; }
                int cortes = 0, u = -1, w = -1;
                for (int i = 0; i < 4; i++)
                    if (in[i] != in[(i + 1) % 4]) { cortes++; if (u < 0) u = e[i]; else w = e[i]; }
                if (cortes == 2) {
                    vec[u][cnt[u]++] = w; vec[w][cnt[w]++] = u;
                } else if (cortes == 4) {                   // cara ambigua: se rodea cada esquina interior
                    for (int i = 0; i < 4; i++)
                        if (in[i]) {
                            int p = e[(i + 3) % 4], r = e[i];
                            vec[p][cnt[p]++] = r; vec[r][cnt[r]++] = p;
                        }
                }
            }
        java.util.List<int[]> res = new java.util.ArrayList<>();
        boolean[] visto = new boolean[12];
        for (int ini = 0; ini < 12; ini++) {
            if (cnt[ini] != 2 || visto[ini]) continue;
            int[] lazo = new int[12];
            int len = 0, prev = -1, cur = ini;
            do {
                lazo[len++] = cur;
                visto[cur] = true;
                int sig = vec[cur][0] != prev ? vec[cur][0] : vec[cur][1];
                prev = cur;
                cur = sig;
            } while (cur != ini);
            lazo = Arrays.copyOf(lazo, len);
            // orientacion: la normal del poligono debe apuntar hacia fuera del solido
            double[] nrm = new double[3], gra = new double[3];
            for (int i = 0; i < len; i++) {
                double[] p = medio(lazo[i]), r = medio(lazo[(i + 1) % len]);
                nrm[0] += p[1] * r[2] - p[2] * r[1];
                nrm[1] += p[2] * r[0] - p[0] * r[2];
                nrm[2] += p[0] * r[1] - p[1] * r[0];
                int c0 = E0[lazo[i]], c1 = E1[lazo[i]];
                int signo = (mascara >> c0 & 1) == 1 ? 1 : -1;   // de la esquina interior a la exterior
                for (int k = 0; k < 3; k++) gra[k] += signo * ((c1 >> k & 1) - (c0 >> k & 1));
            }
            if (nrm[0] * gra[0] + nrm[1] * gra[1] + nrm[2] * gra[2] < 0)
                for (int i = 0; i < len / 2; i++) { int t = lazo[i]; lazo[i] = lazo[len - 1 - i]; lazo[len - 1 - i] = t; }
            res.add(lazo);
        }
        return res.toArray(new int[0][]);
    }

    static double[] medio(int e) {
        double[] r = new double[3];
        for (int k = 0; k < 3; k++) r[k] = ((E0[e] >> k & 1) + (E1[e] >> k & 1)) / 2.0;
        return r;
    }

    static Malla marchingCubes(float[] g, int NX, int NY, int NZ, double h, double ox, double oy, double oz) {
        Malla m = new Malla();
        HashMap<Long, Integer> indice = new HashMap<>(1 << 20);
        int[] des = new int[8];
        for (int c = 0; c < 8; c++) des[c] = (c & 1) * NY * NZ + (c >> 1 & 1) * NZ + (c >> 2 & 1);
        int[] id = new int[12];
        for (int i = 0; i < NX - 1; i++)
            for (int j = 0; j < NY - 1; j++)
                for (int k = 0; k < NZ - 1; k++) {
                    int base = (i * NY + j) * NZ + k;
                    int mascara = 0;
                    for (int c = 0; c < 8; c++) if (g[base + des[c]] < 0) mascara |= 1 << c;
                    if (mascara == 0 || mascara == 255) continue;
                    for (int[] lazo : CASOS[mascara]) {
                        int len = lazo.length;
                        for (int q = 0; q < len; q++) {
                            int e = lazo[q], c0 = E0[e];
                            int n0 = base + des[c0];
                            long clave = (long) n0 * 3 + EJE[e];
                            Integer vi = indice.get(clave);
                            if (vi == null) {
                                double v0 = g[n0], v1 = g[base + des[E1[e]]];
                                double t = limitar(v0 / (v0 - v1), 0.02, 0.98);
                                double x = ox + (i + (c0 & 1)) * h, y = oy + (j + (c0 >> 1 & 1)) * h, z = oz + (k + (c0 >> 2 & 1)) * h;
                                if (EJE[e] == 0) x += t * h; else if (EJE[e] == 1) y += t * h; else z += t * h;
                                vi = m.vertice(x, y, z);
                                indice.put(clave, vi);
                            }
                            id[q] = vi;
                        }
                        if (len == 3) {
                            m.triangulo(id[0], id[1], id[2]);
                        } else if (len == 4) {
                            m.triangulo(id[0], id[1], id[2]);
                            m.triangulo(id[0], id[2], id[3]);
                        } else {                            // poligono grande: abanico desde su centro
                            double cx = 0, cy = 0, cz = 0;
                            for (int q = 0; q < len; q++) { cx += m.v[3 * id[q]]; cy += m.v[3 * id[q] + 1]; cz += m.v[3 * id[q] + 2]; }
                            int centro = m.vertice(cx / len, cy / len, cz / len);
                            for (int q = 0; q < len; q++) m.triangulo(centro, id[q], id[(q + 1) % len]);
                        }
                    }
                }
        return m;
    }

    // ---- Simplificacion por colapso de aristas (metrica de error cuadrico) -----------------
    static final class Cand implements Comparable<Cand> {
        double costo, x, y, z;
        int a, b, va, vb;
        public int compareTo(Cand o) { return Double.compare(costo, o.costo); }
    }

    static final class Simplificador {
        final double[] p, Q;
        final int[] f;
        final int nv, nf;
        final int[][] vf;
        final int[] vfn, ver, marcaA, marcaB;
        final boolean[] fBorrada, vBorrado;
        final double tol2;
        final PriorityQueue<Cand> cola = new PriorityQueue<>();
        int sello = 0;

        Simplificador(Malla m, double tol) {
            p = m.v; f = m.f; nv = m.nv; nf = m.nf; tol2 = tol * tol;
            Q = new double[10 * nv];
            vfn = new int[nv]; ver = new int[nv]; marcaA = new int[nv]; marcaB = new int[nv];
            fBorrada = new boolean[nf]; vBorrado = new boolean[nv];
            for (int i = 0; i < 3 * nf; i++) vfn[f[i]]++;
            vf = new int[nv][];
            for (int i = 0; i < nv; i++) { vf[i] = new int[vfn[i]]; vfn[i] = 0; }
            double[] n = new double[4];
            for (int t = 0; t < nf; t++) {
                for (int k = 0; k < 3; k++) { int v = f[3 * t + k]; vf[v][vfn[v]++] = t; }
                if (normal(t, -1, 0, 0, 0, n) < 1e-12) continue;
                int a = f[3 * t];
                double d = -(n[0] * p[3 * a] + n[1] * p[3 * a + 1] + n[2] * p[3 * a + 2]);
                double[] pl = {n[0], n[1], n[2], d};
                for (int k = 0; k < 3; k++) {
                    int o = 10 * f[3 * t + k], c = 0;
                    for (int r = 0; r < 4; r++) for (int s = r; s < 4; s++) Q[o + c++] += pl[r] * pl[s];
                }
            }
        }

        /** Normal unitaria del triangulo t (opcionalmente con el vertice "mov" desplazado). Devuelve su longitud previa. */
        double normal(int t, int mov, double mx, double my, double mz, double[] n) {
            double[] q = new double[9];
            for (int k = 0; k < 3; k++) {
                int v = f[3 * t + k];
                if (v == mov) { q[3 * k] = mx; q[3 * k + 1] = my; q[3 * k + 2] = mz; }
                else { q[3 * k] = p[3 * v]; q[3 * k + 1] = p[3 * v + 1]; q[3 * k + 2] = p[3 * v + 2]; }
            }
            double ux = q[3] - q[0], uy = q[4] - q[1], uz = q[5] - q[2];
            double wx = q[6] - q[0], wy = q[7] - q[1], wz = q[8] - q[2];
            double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l > 0) { n[0] = nx / l; n[1] = ny / l; n[2] = nz / l; } else { n[0] = n[1] = n[2] = 0; }
            return l;
        }

        /** Quita de la lista del vertice v las caras ya borradas y devuelve cuantas quedan. */
        int vivas(int v) {
            int w = 0;
            int[] l = vf[v];
            for (int i = 0; i < vfn[v]; i++) if (!fBorrada[l[i]]) l[w++] = l[i];
            return vfn[v] = w;
        }

        void proponer(int a, int b) {
            double[] q = new double[10];
            for (int i = 0; i < 10; i++) q[i] = Q[10 * a + i] + Q[10 * b + i];
            double ax = p[3 * a], ay = p[3 * a + 1], az = p[3 * a + 2];
            double bx = p[3 * b], by = p[3 * b + 1], bz = p[3 * b + 2];
            double len = Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
            if (len >= 3.0) return;
            double mx = (ax + bx) / 2, my = (ay + by) / 2, mz = (az + bz) / 2;
            // punto optimo: minimiza el error cuadrico (con una pizca de regularizacion hacia el punto medio)
            double r = 1e-3;
            double a00 = q[0] + r, a01 = q[1], a02 = q[2], a11 = q[4] + r, a12 = q[5], a22 = q[7] + r;
            double r0 = -q[3] + r * mx, r1 = -q[6] + r * my, r2 = -q[8] + r * mz;
            double det = a00 * (a11 * a22 - a12 * a12) - a01 * (a01 * a22 - a12 * a02) + a02 * (a01 * a12 - a11 * a02);
            double x = mx, y = my, z = mz;
            if (Math.abs(det) > 1e-12) {
                x = (r0 * (a11 * a22 - a12 * a12) - a01 * (r1 * a22 - a12 * r2) + a02 * (r1 * a12 - a11 * r2)) / det;
                y = (a00 * (r1 * a22 - a12 * r2) - r0 * (a01 * a22 - a12 * a02) + a02 * (a01 * r2 - r1 * a02)) / det;
                z = (a00 * (a11 * r2 - r1 * a12) - a01 * (a01 * r2 - r1 * a02) + r0 * (a01 * a12 - a11 * a02)) / det;
                double dd = Math.sqrt((x - mx) * (x - mx) + (y - my) * (y - my) + (z - mz) * (z - mz));
                if (dd > len) { x = mx; y = my; z = mz; }
            }
            double costo = q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x + q[4] * y * y
                    + 2 * q[5] * y * z + 2 * q[6] * y + q[7] * z * z + 2 * q[8] * z + q[9];
            if (costo >= tol2) return;
            Cand c = new Cand();
            c.costo = costo; c.x = x; c.y = y; c.z = z; c.a = a; c.b = b; c.va = ver[a]; c.vb = ver[b];
            cola.add(c);
        }

        boolean valido(Cand c) {
            int a = c.a, b = c.b;
            sello++;
            int gradoA = 0, gradoB = 0, comunes = 0;
            int na = vivas(a), nb = vivas(b);
            for (int i = 0; i < na; i++)
                for (int k = 0; k < 3; k++) {
                    int u = f[3 * vf[a][i] + k];
                    if (u != a && marcaA[u] != sello) { marcaA[u] = sello; gradoA++; }
                }
            if (marcaA[b] != sello) return false;           // ya no son vecinos
            for (int i = 0; i < nb; i++)
                for (int k = 0; k < 3; k++) {
                    int u = f[3 * vf[b][i] + k];
                    if (u != b && marcaB[u] != sello) {
                        marcaB[u] = sello; gradoB++;
                        if (u != a && marcaA[u] == sello) comunes++;
                    }
                }
            // condicion topologica: la arista debe tener exactamente 2 vecinos comunes (malla sigue siendo variedad)
            if (comunes != 2 || gradoA <= 3 || gradoB <= 3) return false;
            // ningun triangulo vecino puede voltearse ni quedar degenerado
            double[] n0 = new double[3], n1 = new double[3];
            for (int lado = 0; lado < 2; lado++) {
                int v = lado == 0 ? a : b, otro = lado == 0 ? b : a;
                for (int i = 0; i < vfn[v]; i++) {
                    int t = vf[v][i];
                    if (f[3 * t] == otro || f[3 * t + 1] == otro || f[3 * t + 2] == otro) continue;
                    double l0 = normal(t, -1, 0, 0, 0, n0);
                    double l1 = normal(t, v, c.x, c.y, c.z, n1);
                    if (l1 < 1e-9) return false;
                    if (l0 > 1e-12 && n0[0] * n1[0] + n0[1] * n1[1] + n0[2] * n1[2] < 0.3) return false;
                }
            }
            return true;
        }

        void colapsar(Cand c) {
            int a = c.a, b = c.b;
            int na = vfn[a], nb = vfn[b];                   // ya compactadas en valido()
            int[] nueva = new int[na + nb];
            int w = 0;
            for (int i = 0; i < na; i++) {
                int t = vf[a][i];
                if (f[3 * t] == b || f[3 * t + 1] == b || f[3 * t + 2] == b) fBorrada[t] = true;
                else nueva[w++] = t;
            }
            for (int i = 0; i < nb; i++) {
                int t = vf[b][i];
                if (fBorrada[t]) continue;
                for (int k = 0; k < 3; k++) if (f[3 * t + k] == b) f[3 * t + k] = a;
                nueva[w++] = t;
            }
            vf[a] = nueva; vfn[a] = w;
            vf[b] = null; vfn[b] = 0; vBorrado[b] = true;
            p[3 * a] = c.x; p[3 * a + 1] = c.y; p[3 * a + 2] = c.z;
            for (int i = 0; i < 10; i++) Q[10 * a + i] += Q[10 * b + i];
            ver[a]++;
            sello++;
            for (int i = 0; i < w; i++)
                for (int k = 0; k < 3; k++) {
                    int u = f[3 * nueva[i] + k];
                    if (u != a && marcaA[u] != sello) { marcaA[u] = sello; proponer(a, u); }
                }
        }

        void ejecutar() {
            for (int t = 0; t < nf; t++)
                for (int k = 0; k < 3; k++) {
                    int a = f[3 * t + k], b = f[3 * t + (k + 1) % 3];
                    if (a < b) proponer(a, b);
                }
            while (!cola.isEmpty()) {
                Cand c = cola.poll();
                if (vBorrado[c.a] || vBorrado[c.b] || ver[c.a] != c.va || ver[c.b] != c.vb) continue;
                if (valido(c)) colapsar(c);
            }
        }
    }

    static void simplificar(Malla m, double tol) {
        Simplificador s = new Simplificador(m, tol);
        s.ejecutar();
        int[] nuevo = new int[m.nv];
        int nv = 0, nf = 0;
        for (int i = 0; i < m.nv; i++) {
            if (s.vBorrado[i]) { nuevo[i] = -1; continue; }
            m.v[3 * nv] = m.v[3 * i]; m.v[3 * nv + 1] = m.v[3 * i + 1]; m.v[3 * nv + 2] = m.v[3 * i + 2];
            nuevo[i] = nv++;
        }
        for (int t = 0; t < m.nf; t++) {
            if (s.fBorrada[t]) continue;
            for (int k = 0; k < 3; k++) m.f[3 * nf + k] = nuevo[m.f[3 * t + k]];
            nf++;
        }
        m.nv = nv;
        m.nf = nf;
    }

    // ---- STL binario ------------------------------------------------------------------------
    static void escribirStl(Malla m, String archivo) throws IOException {
        try (OutputStream out = Files.newOutputStream(Paths.get(archivo))) {
            byte[] cab = new byte[80];
            byte[] txt = "F1 2026 generico 1:43 - unidades: mm - generado con F1Car.java".getBytes("US-ASCII");
            System.arraycopy(txt, 0, cab, 0, txt.length);
            out.write(cab);
            ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(m.nf);
            out.write(bb.array());
            int porBloque = 4096;
            bb = ByteBuffer.allocate(50 * porBloque).order(ByteOrder.LITTLE_ENDIAN);
            for (int t = 0; t < m.nf; t++) {
                int a = 3 * m.f[3 * t], b = 3 * m.f[3 * t + 1], c = 3 * m.f[3 * t + 2];
                double ux = m.v[b] - m.v[a], uy = m.v[b + 1] - m.v[a + 1], uz = m.v[b + 2] - m.v[a + 2];
                double wx = m.v[c] - m.v[a], wy = m.v[c + 1] - m.v[a + 1], wz = m.v[c + 2] - m.v[a + 2];
                double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
                double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l > 0) { nx /= l; ny /= l; nz /= l; }
                bb.putFloat((float) nx).putFloat((float) ny).putFloat((float) nz);
                for (int o : new int[]{a, b, c}) bb.putFloat((float) m.v[o]).putFloat((float) m.v[o + 1]).putFloat((float) m.v[o + 2]);
                bb.putShort((short) 0);
                if (bb.remaining() == 0) { out.write(bb.array()); bb.clear(); }
            }
            out.write(bb.array(), 0, bb.position());
        }
    }

    /** Comprueba que la malla es cerrada y bien orientada, e informa medidas y volumen. */
    static void verificar(Malla m) {
        HashMap<Long, Integer> aristas = new HashMap<>(m.nf * 4);
        for (int t = 0; t < m.nf; t++)
            for (int k = 0; k < 3; k++) {
                long a = m.f[3 * t + k], b = m.f[3 * t + (k + 1) % 3];
                aristas.merge(a * m.nv + b, 1, Integer::sum);
            }
        int malas = 0;
        for (java.util.Map.Entry<Long, Integer> e : aristas.entrySet()) {
            long a = e.getKey() / m.nv, b = e.getKey() % m.nv;
            Integer gemela = aristas.get(b * m.nv + a);
            if (e.getValue() != 1 || gemela == null || gemela != 1) malas++;
        }
        double vol = 0;
        for (int t = 0; t < m.nf; t++) {
            int a = 3 * m.f[3 * t], b = 3 * m.f[3 * t + 1], c = 3 * m.f[3 * t + 2];
            vol += m.v[a] * (m.v[b + 1] * m.v[c + 2] - m.v[b + 2] * m.v[c + 1])
                    - m.v[a + 1] * (m.v[b] * m.v[c + 2] - m.v[b + 2] * m.v[c])
                    + m.v[a + 2] * (m.v[b] * m.v[c + 1] - m.v[b + 1] * m.v[c]);
        }
        double[] l = m.limites();
        System.out.printf("Triangulos: %d | vertices: %d | aristas defectuosas: %d (0 = malla estanca)%n", m.nf, m.nv, malas);
        System.out.printf("Medidas (largo x ancho x alto): %.2f x %.2f x %.2f mm | volumen: %.2f cm3%n",
                l[3] - l[0], l[4] - l[1], l[5] - l[2], vol / 6 / 1000);
    }
}
