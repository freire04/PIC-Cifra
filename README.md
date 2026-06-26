# PIC - Cifra de Ficheiros

Aplicacao desktop em Java para cifrar e decifrar ficheiros com suporte a
tokens criptograficos atraves de PKCS#11. O projeto foi desenvolvido no ambito
do Projeto Integrador de Curso do IST.

O sistema combina:

- interface grafica em JavaFX;
- gestao de contas e permissoes por roles;
- cifragem e decifragem de ficheiros com AES-GCM;
- integracao com PKCS#11 usando o provider `SunPKCS11`;
- suporte a SoftHSM2 para desenvolvimento e testes;
- registo persistente de auditoria em NDJSON.

## Funcionalidades

- Autenticacao de utilizadores.
- Criacao automatica de uma conta inicial `admin` no primeiro arranque.
- Gestao de contas por utilizadores com role `ADMIN`.
- Separacao de permissoes entre `USER`, `ADMIN` e `AUDITOR`.
- Cifragem e decifragem de ficheiros apenas para utilizadores com role `USER`.
- Desbloqueio do token criptografico atraves de PIN.
- Consulta e filtragem de logs por utilizadores com role `AUDITOR`.
- Persistencia local de contas e logs.

## Tecnologias

- Java 21
- Maven
- JavaFX 21
- Jackson
- JUnit 5
- JaCoCo
- PKCS#11 / SunPKCS11
- SoftHSM2

## Requisitos

Para compilar e executar a aplicacao:

- JDK 21
- Maven

Para executar operacoes reais de cifra/decifra com token:

- SoftHSM2, ou outro token/HSM compativel com PKCS#11
- ficheiro de configuracao PKCS#11 valido
- token inicializado com PIN

## Como correr

Instalar dependencias e compilar:

```powershell
mvn clean compile
```

Executar a aplicacao JavaFX:

```powershell
mvn javafx:run
```

No primeiro arranque, se ainda nao existir nenhum ficheiro de contas, a
aplicacao cria uma conta inicial:

```text
username: admin
```

A palavra-passe temporaria e apresentada numa janela da aplicacao e deve ser
alterada no primeiro login.

## Dados persistidos

Por omissao, a aplicacao guarda dados na pasta `data/`:

```text
data/accounts.json
data/logs.ndjson
```

O ficheiro `accounts.json` contem as contas de utilizador. As palavras-passe
nao sao guardadas em texto claro; apenas e persistido o respetivo hash.

O ficheiro `logs.ndjson` contem os registos de auditoria, um evento por linha.

## PKCS#11 / SoftHSM2

A implementacao concreta de `CryptoService` e `PKCS11Service`. Nao existe
fallback local/JCE para cifrar ficheiros: as operacoes criptograficas dependem
do provider PKCS#11 configurado.

O ficheiro de configuracao por omissao e:

```text
pkcs11.cfg
```

Este ficheiro deve apontar para a biblioteca PKCS#11 usada pelo token. Para
SoftHSM2 em Windows, o projeto assume por omissao algo semelhante a:

```text
library = C:/SoftHSM2/lib/softhsm2-x64.dll
```

Se o SoftHSM2 estiver instalado noutro local, ajustar o caminho no ficheiro
`pkcs11.cfg`.

### Configurar SoftHSM2

Definir as variaveis de ambiente na sessao PowerShell:

```powershell
$env:SOFTHSM2_CONF="C:\SoftHSM2\etc\softhsm2.conf"
$env:PATH="C:\SoftHSM2\lib;C:\SoftHSM2\bin;$env:PATH"
```

Inicializar um token:

```powershell
softhsm2-util --init-token --slot 0 --label PIC --so-pin <SO_PIN> --pin <TOKEN_PIN>
```

Notas:

- `<SO_PIN>` e o PIN de administracao do token.
- `<TOKEN_PIN>` e o PIN usado pela aplicacao para abrir sessao no token.
- Nunca guardar PINs reais no repositorio.


## Testes

Executar a suite de testes:

```powershell
mvn test
```

Gerar o relatorio de cobertura JaCoCo:

```powershell
mvn test
```

Depois abrir:

```text
target/site/jacoco/index.html
```

### Teste real com SoftHSM2

Depois de inicializar o token, correr:

```powershell
$env:SOFTHSM2_CONF="C:\SoftHSM2\etc\softhsm2.conf"
$env:PATH="C:\SoftHSM2\lib;C:\SoftHSM2\bin;$env:PATH"

mvn -q -Dtest=PKCS11ServiceIntegrationTest -Dpic.pkcs11.test.pin=<TOKEN_PIN> test
```

O teste abre sessao no token, cria a chave AES no SoftHSM2 se ainda nao existir,
cifra um ficheiro temporario, decifra o resultado e compara com o original.

## Estrutura do projeto

```text
src/main/java/pt/tecnico/pic
|-- application     # fachada da aplicacao e validacao de permissoes
|-- crypto          # integracao PKCS#11 e operacoes criptograficas
|-- domain          # entidades e enums de dominio
|-- dto             # objetos usados entre camadas e interface
|-- presentation    # JavaFX, SceneManager e controllers
|-- service         # regras de negocio
|-- store           # persistencia local em JSON/NDJSON
`-- util            # utilitarios comuns

src/main/resources
|-- fxml            # vistas JavaFX
`-- css             # estilos da aplicacao
```

## Roles

- `USER`: pode cifrar e decifrar ficheiros, desde que o token esteja desbloqueado.
- `ADMIN`: pode criar contas, alterar roles, ativar/desativar contas e repor passwords.
- `AUDITOR`: pode consultar e filtrar logs de auditoria.

Em cada sessao existe apenas uma role ativa. Um utilizador com varias roles deve
selecionar qual pretende usar antes de executar operacoes.

## Auditoria

O historico de auditoria e guardado em `data/logs.ndjson`. Cada linha contem um
evento independente em formato JSON.

A dashboard de auditoria permite filtrar logs por:

- username;
- role ativa no momento da operacao;
- tipo de acao;
- resultado;
- intervalo de datas.

### Politica de logging `VIEW_LOGS`

A dashboard de auditoria nao cria um novo evento `VIEW_LOGS` sempre que o
auditor carrega em refresh ou altera um filtro. A politica usada e:

- registar um evento `VIEW_LOGS` bem-sucedido, no maximo, uma vez por sessao
  autenticada com a role `AUDITOR` selecionada;
- reiniciar essa politica depois de login, logout ou selecao bem-sucedida de
  outra role;
- evitar spam de logs durante refreshes e alteracoes de filtro na mesma sessao;
- continuar a registar individualmente tentativas de acesso falhadas.

Esta politica mantem o historico de auditoria util sem esconder que o auditor
acedeu ao painel de logs.

## Notas de seguranca

- A chave AES usada na cifra e guardada no token PKCS#11.
- O PIN desbloqueia o token, mas nao e usado como chave de cifra.
- As palavras-passe das contas nao sao guardadas em texto claro.
- Os logs devem conter apenas informacao necessaria para auditoria.
- Caminhos completos, PINs, passwords, chaves e conteudo de ficheiros nao devem
  ser expostos em logs.

## Problemas comuns

### `PKCS#11 configuration file not found`

Confirmar que `pkcs11.cfg` existe na raiz do projeto ou indicar outro caminho
com `-Dpic.pkcs11.config` ou `PIC_PKCS11_CONFIG`.

### `SunPKCS11 provider is not available`

Confirmar que a aplicacao esta a correr com um JDK que inclui o provider
`SunPKCS11`.

### Token nao desbloqueia

Confirmar que:

- o SoftHSM2 esta instalado;
- `SOFTHSM2_CONF` aponta para a configuracao certa;
- o token foi inicializado;
- o PIN introduzido e o PIN de utilizador do token;
- o caminho `library` em `pkcs11.cfg` esta correto.
