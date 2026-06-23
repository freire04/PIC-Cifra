# PIC---Cifra-de-ficheiros
Este repositorio foi usado para o desenvolvimento do PIC do IST dos alunos...

## PKCS#11 / SoftHSM2

O S1-09 usa apenas `PKCS11Service` como implementacao concreta de `CryptoService`.
Nao existe fallback local/JCE para cifrar ficheiros.

Para validar com token real:

1. Instalar SoftHSM2.
2. Ajustar o caminho `library` em `pkcs11.cfg`.
3. Definir a configuracao e bibliotecas SoftHSM2 na sessao:

```powershell
$env:SOFTHSM2_CONF="C:\SoftHSM2\etc\softhsm2.conf"
$env:PATH="C:\SoftHSM2\lib;C:\SoftHSM2\bin;$env:PATH"
```

4. Inicializar um token, por exemplo:

```powershell
softhsm2-util --init-token --slot 0 --label PIC --so-pin <SO_PIN> --pin <TOKEN_PIN>
```

Tambem e possivel indicar outro ficheiro de configuracao com
`-Dpic.pkcs11.config=C:\caminho\pkcs11.cfg` ou a variavel `PIC_PKCS11_CONFIG`.
Nao guardar PINs reais no repositorio.

### Teste real com SoftHSM2

Depois de inicializar o token, correr:

```powershell
$env:SOFTHSM2_CONF="C:\SoftHSM2\etc\softhsm2.conf"
$env:PATH="C:\SoftHSM2\lib;C:\SoftHSM2\bin;$env:PATH"

mvn -q -Dtest=PKCS11ServiceIntegrationTest -Dpic.pkcs11.test.pin=<TOKEN_PIN> test
```

O teste abre sessao no token, cria a chave AES no SoftHSM2 se ainda nao existir,
cifra um ficheiro temporario, decifra o resultado e compara com o original.


## S2-04 - VIEW_LOGS logging policy

The audit dashboard must not create a new VIEW_LOGS entry every time the user clicks refresh or changes a filter. The selected policy is:

- register a successful VIEW_LOGS event at most once per authenticated session and selected AUDITOR role;
- reset the policy after login, logout, or a successful role selection;
- repeated refreshes and filter changes in the same audit dashboard session should reuse the existing VIEW_LOGS event instead of creating log spam.
- failed access attempts are still logged individually.

This policy keeps the audit trail useful without hiding the fact that the auditor accessed the log dashboard.
