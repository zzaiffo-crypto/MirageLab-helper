package miragelab;
/** Explicit data-quality resource view. Capacity and item counts require a separately mapped inventory component. */
final class ResourceMonitor {
    private ResourceMonitor() { }
    static String summary(){
        Double food=GameDirectVitals.lastNourishment();
        String nutrition=food==null?"Food ?":String.format(java.util.Locale.ROOT,"Food %.0f/60",food);
        return nutrition+" | Cap/inventario: nao mapeados";
    }
}
