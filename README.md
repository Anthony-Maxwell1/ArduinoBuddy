# ArduinoBuddy
## Code Arduino on your phone.
**[WIP]**
Related repository:
- [Arduino CLI fork](https://github.com/Anthony-Maxwell1/arduino-cli_arduinobuddyfork), ported to run on android with this application providing a native interface.

### Why port arduino-cli? Wouldn't it have been simpler to make it in kotlin?
The arduino-cli provides compatibility to many different boards.

### What features are complete?
- Porting arduino-cli to android. This is done using gomobile bind, and a patched interface is added for sending serial back to the application to be handled natively.

### What features need to be completed?
- Native serial. The plan is to use [this](https://github.com/mik3y/usb-serial-for-android) library for native serial, which supports many boards serial interface.
- Cores. Cores manage the boards themselves, avrdude for arduino/boards utilizing avr and esptool for esp32.

### Cores
- esptool has not been ported yet, however could be done by patching to replace the relevant serial library with a callback to the application (similar to arduino-cli) as well as replacing the compilation tool with an equivalent that can run natively. If worse comes to worst, we can use the cloud for compilation. It can be ran using a python emulation library.
- avr-dude has not been ported yet. Have not thought about this one just yet. C can run on pretty much anything though, and can be included as a .so similar to arduino-cli with some patches.

### Compilers
Android compilation should in theory be one of the easier parts, due to compilers being mostly logic, and C being able to run on practically anything. Compilers like gcc or Clang can easily be compiled to run on android as a shared library, the hardest part would be simply debugging it when it inevitably breaks down on the low processing power. However, not many patches should be needed as it is mostly (if not all) processing and logic.
