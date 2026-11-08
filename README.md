# Voice Erase (Fabric 1.20.1)

O jogo escuta o microfone (reconhecimento de voz offline em português, via Vosk).
Tudo que você falar que seja nome de **bloco, item ou mob** some do mundo, pra sempre.

## Como compilar
Requisitos: JDK 17 e Gradle 8.x (ou abra a pasta no IntelliJ IDEA, que configura tudo).

    gradle wrapper --gradle-version 8.5   # só na primeira vez
    ./gradlew build

O .jar fica em `build/libs/voiceerase-1.0.0.jar`.
Para testar direto: `./gradlew runClient`.

## Como usar
1. Instale Fabric Loader 0.15.11+ e o **Fabric API 0.92.2+1.20.1** no Minecraft 1.20.1.
2. Coloque o .jar na pasta `mods`.
3. Em Opções > Idioma, escolha **Português (Brasil)** (o mod casa o que você fala com os nomes traduzidos).
4. Entre num mundo. Na primeira vez ele baixa o modelo de voz (~30 MB) para `.minecraft/voiceerase/model`.
5. Fale: "zumbi", "pedra", "terra", "creeper"... e vai sumindo. Tecla **K** liga/desliga o microfone.

Em servidor: o mod precisa estar no servidor e em cada cliente. O microfone de cada jogador apaga para todos.

## Comandos
- `/voiceerase list`: lista o que já foi apagado
- `/voiceerase reset` (op): zera a lista (o que já sumiu não volta)

## Observações
- Blocos somem num raio de 8 chunks ao redor dos jogadores na hora, e nos demais chunks quando carregam.
- Itens somem dos inventários, do baú de ender e do chão. Itens dentro de baús comuns não são varridos.
- Mobs são removidos e não nascem mais.
- Cuidado: falar "terra" ou "água" com você em cima pode dar ruim. :)
