# Menú semanal + Compra automática — v1.2

Esta versión añade:
- Platos con ingredientes y cantidades.
- Menú semanal lunes-domingo.
- Lista de compra manual.
- Botón "Generar compra desde el menú".
- Agrupación de ingredientes con el mismo nombre.
- Guardado local.
- Workflow de GitHub Actions para generar automáticamente un APK.

## Cómo obtener el APK sin Android Studio

1. Crea un repositorio nuevo en GitHub.
2. Sube todo el contenido de esta carpeta al repositorio.
3. En GitHub abre la pestaña **Actions**.
4. Ejecuta el workflow **Crear APK**.
5. Cuando termine, abre la ejecución y descarga el artefacto **MenuSemanal-debug**.
6. Dentro encontrarás `app-debug.apk`, que puedes pasar al móvil e instalar.

El workflow instala Java, Gradle y Android SDK en GitHub Actions, por lo que tu ordenador no necesita Android Studio.

## Uso de ingredientes

Al crear un plato puedes escribir, una línea por ingrediente:

patatas | 1 kg
huevos | 6
aceite | 100 ml

Después asigna los platos a los días y entra en **Compra** → **Generar compra desde el menú**.

Nota: esta primera versión suma cantidades como texto (por ejemplo "500 g + 1 kg") en vez de convertir automáticamente unidades. Esa mejora puede añadirse después.
