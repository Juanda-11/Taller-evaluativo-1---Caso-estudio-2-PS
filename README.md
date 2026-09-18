# Caso de estudio 2

## 1. Abstract Factory — familias por tipo de terminal

Problema que resuelve: cada terminal (granel sólido, contenedores, líquidos)
define de forma *inseparable* tres productos: equipo de cargue, validador de
estabilidad y documento de embarque. Si estos tres objetos se crearan por
separado con `if/else`, sería fácil terminar con combinaciones inconsistentes
(por ejemplo, una grúa pórtico con un certificado de humedad). El Abstract
Factory obliga a crear siempre la familia completa y correcta, y el resto del
programa (`printPlan`) solo conoce las interfaces `LoadingEquipment`,
`StabilityValidator` y `ShippingDocument`, nunca las clases concretas.

*Dónde está:*
- Interfaces de producto: `LoadingEquipment`, `StabilityValidator`, `ShippingDocument`.
- 9 productos concretos (3 por cada terminal): p. ej. `ConveyorBeltBT3` /
  `BulkSolidStabilityValidator` / `BulkSolidShippingDocument` para granel sólido,
  y sus equivalentes para contenedores y líquidos.
- Fábrica abstracta: interfaz `TerminalFactory` con el método estático
  `forType(TerminalType)` que entrega la fábrica concreta correcta.
- Fábricas concretas: `BulkSolidTerminalFactory`, `ContainerTerminalFactory`,
  `LiquidTerminalFactory`.

## 2. Factory Method — creación de unidades de carga desde el manifiesto

Problema que resuelve: el manifiesto llega como texto plano cuyo formato y
fórmula de peso cambian según el tipo de carga (contenedor, granel, líquido).
En vez de un único método gigante con condicionales (el "método de 400
líneas" que menciona el caso), se define un algoritmo plantilla único
(`process`) que lee línea por línea, delega la creación del objeto concreto a
un método fábrica abstracto (`createUnit`) y nunca detiene el proceso ante una
línea inválida: la reporta como rechazada y continúa.

*Dónde está:*
- Clase abstracta `ManifestRegistrar` con el método plantilla `process(List<String>)`
  (público, `final`) y el método fábrica `protected abstract CargoUnit createUnit(String)`.
- Implementaciones concretas: `ContainerRegistrar`, `BulkRegistrar`, `LiquidRegistrar`,
  cada uno con su propio parseo (`split(";")`) y su propia fórmula de peso.
- Jerarquía de producto creada por el factory method: `CargoUnit` (abstracta) y
  `ContainerUnit`, `BulkUnit`, `LiquidUnit`.
- Líneas mal formadas: se capturan como `IllegalArgumentException` dentro de
  `createUnit` y se registran en la lista `rejected` (record `RejectedLine`),
  sin detener el recorrido del manifiesto.

## 3. Builder — armado del Plan de Estiba

Problema que resuelve: `StowagePlan` tiene 6 campos obligatorios y 6
opcionales; construirlo con un constructor telescópico sería ilegible y
propenso a errores (por ejemplo, olvidar la matrícula de la barcaza). El
Builder arma el plan paso a paso con una API fluida y concentra **toda** la
validación cruzada en un único punto (`build()`), devolviendo un objeto
**inmutable** (sin setters, sin getters mutables sobre las listas internas
gracias a `List.copyOf`).

*Dónde está:*
- Clase `StowagePlan` (inmutable, constructor privado) con su clase estática
  anidada `Builder`.
- Campos obligatorios: `planNumber`, `departureDate`, `bargeId`, `terminalType`,
  `holds` (distribución de bodegas), `totalWeight`.
- Campos opcionales: `loadingSequence`, `timeWindow`, `assignedTugboat`,
  `safetyNotes`, `declaredDraftRestriction`, `plannerName`.
- `build()` lanza `IllegalStateException` si:
  - falta cualquier campo obligatorio (incluida la matrícula de la barcaza,
    demostrado en `runBuilderValidationDemo`),
  - la fecha de zarpe es anterior a `LocalDate.now()`,
  - ninguna bodega tiene unidades asignadas.

### Componente algorítmico (no es un patrón, pero es obligatorio)

Todo vive en `StowagePlanningService`:
- `distributeFirstFitDecreasing`: ordena las unidades por peso descendente y
  las asigna a la primera bodega (orden B1→B2→B3→B4) con capacidad suficiente;
  lo que no cabe en ninguna queda en la lista de "carga no embarcada".
- `calculateImbalancePercent` / `calculateTonsToMove`: reglas de balance
  proa/popa (sección 2.4 del caso).
- `calculateDraftMeters`: calado estimado (sección 2.5).
- `formatLoadingTime`: horas y minutos de cargue según la tasa del equipo.
- `generateLoadingSequence`: secuencia numerada bodega por bodega, de mayor a
  menor peso dentro de cada bodega.

### Programa de demostración (`main`)

1. **Contenedores:** procesa un manifiesto de 12 líneas (10 válidas + 2
   inválidas: una que no corresponde al registrador y otra con "pies" no
   numérico) y genera el plan completo — queda **APROBADO** (desbalance
   0.23 %, calado 1.07 m), con 2 unidades que no caben en ninguna bodega.
2. **Granel sólido:** procesa 5 lotes cuyo peso total produce un desbalance de
   44.63 % (límite 8 %), por lo que el plan queda **NO APROBADO** e indica las
   toneladas exactas a trasladar (311.55 t) para llegar justo al límite.
3. **Builder:** intenta construir un plan sin matrícula de barcaza dentro de un
   `try/catch` y muestra el mensaje de `IllegalStateException`.
