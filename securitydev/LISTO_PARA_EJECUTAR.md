## 🚀 INSTRUCCIONES FINALES - BACKEND LISTO

### ✅ LO QUE YA HICE

1. ✅ Limpieza de `build.gradle.kts` (eliminé 2 duplicaciones)
2. ✅ `application.yaml` actualizado para env vars
3. ✅ Todos los archivos Kotlin del backend creados:
   - `CognitoService.kt`
   - `AwsConfig.kt`
   - `RegisterRequest.kt`
   - `HealthController.kt`
   - Endpoints en `UserController.kt` y `UserService.kt` actualizados

---

## 🔧 LO QUE NECESITAS HACER

### PASO 1: Build (en IntelliJ)

1. Abre IntelliJ con el proyecto `securitydev`
2. File → Sync Gradle Files (o clic en "Sync" que aparece)
3. Espera a que descargue `cognitoidentityprovider:2.25.60`
4. Build → Build Project

Debería compilar sin errores.

---

### PASO 2: Crear Variables de Entorno

**Opción A: En IntelliJ (más fácil para desarrollo)**

1. Run → Edit Configurations
2. Click en "SecuritydevApplication" (o tu config de Spring Boot)
3. Pestaña "Environment variables"
4. Agrega estas 3:

```
COGNITO_USER_POOL_ID=us-east-1_X4BJvA1pT
AWS_ACCESS_KEY_ID=TU_ACCESS_KEY_AQUI
AWS_SECRET_ACCESS_KEY=TU_SECRET_KEY_AQUI
```

5. OK

**Opción B: En terminal (PowerShell - permanente en sesión)**

```powershell
$env:COGNITO_USER_POOL_ID="us-east-1_X4BJvA1pT"
$env:AWS_ACCESS_KEY_ID="TU_ACCESS_KEY_AQUI"
$env:AWS_SECRET_ACCESS_KEY="TU_SECRET_KEY_AQUI"
```

---

### PASO 3: Run Backend

En IntelliJ:
- Run → Run "SecuritydevApplication"
- O presiona Shift+F10

Debería ver:
```
2026-07-17 14:30:00 INFO ... Application started
...listening on port 8080
```

---

### PASO 4: Verifica que TODO está UP

En PowerShell:

```powershell
# Test 1: Backend levantado?
curl http://localhost:8080/api/health

# Resultado esperado:
# {"status":"UP","service":"SecurityApp Backend","timestamp":"1721..."}

# Test 2: Cognito conectado?
curl http://localhost:8080/api/health/cognito

# Resultado esperado:
# {"status":"OK","cognitoPoolId":"us-east-1_X4BJvA1pT","hasAwsCredentials":true}
```

Si ves ✅ en ambas → **¡LISTO!**

---

## 🧪 Ahora TESTEA EL REGISTRO COMPLETO

1. **Frontend corre:** http://localhost:5173
2. **Backend corre:** http://localhost:8080
3. **Ve a /register**
4. **Llena formulario:**
   - Nombre: "Test User"
   - Email: "test-TIMESTAMP@gmail.com" (reemplaza TIMESTAMP con número actual)
   - Teléfono: "0939141983"
   - Contraseña: "TempPass123!@#"
   - Confirmar: igual

5. **Haz clic "Crear cuenta"**
6. **Abre Console (F12) y mira logs**

**Esperado:**
```
✅ SignUp exitoso
📝 Iniciando SignUp en Cognito
✅ Cuenta verificada. Ya puedes iniciar sesión
```

Si el email tiene un código de Cognito:
- Ingresa el código
- Debería sincronizar con backend
- ✅ Listo para login

---

## ⚠️ POSIBLES ERRORES Y SOLUCIONES

### "AWS Credentials Not Found"
```
❌ CognitoService no puede conectar a AWS
```
**Solución:** Verifica que env vars están seteadas correctamente
```powershell
echo $env:AWS_ACCESS_KEY_ID
echo $env:AWS_SECRET_ACCESS_KEY
```

### "UserPoolIdNotFound"
```
❌ COGNITO_USER_POOL_ID no está cargado
```
**Solución:** Reinicia IntelliJ y vuelve a Run

### "InvalidPasswordException en Cognito"
```
❌ La contraseña no cumple requisitos
```
**Solución:** Usa: `Security123!@#` (mayúscula, número, especial, 8+ chars)

### 409 Conflict - "Email already exists"
```
❌ El email ya fue registrado antes
```
**Solución:** USA UN EMAIL DIFERENTE cada vez que pruebes

---

## 📝 RESUMEN FINAL

- Backend: Spring Boot 3.4 + Kotlin
- Cognito SDK: 2.25.60 ✅
- AWS SDK: Listo para usar grupos ✅
- Frontend: React + Cognito JS SDK ✅
- Flujo: SignUp → Confirmar → Crear BD + Asignar grupo → Login

**TODO ESTÁ LISTO. Solo falta que ejecutes y pruebes. 🚀**

---

## 🆘 Si algo falla:

1. Abre Console del navegador (F12)
2. Copia TODO lo que ves
3. Copia el output de la terminal de IntelliJ
4. Pásame ambos y debugueo contigo
