#include <Windows.h>
#include <ShlObj.h>
#include <iostream>

#include "AppDataProvider.h"
#include "LogWriter.h"

#define APP_DIRECTORY_NAME L"\\exchange_data\\"

std::wstring Antivirus::appdataDirectory() {
    wchar_t* temp;
    HRESULT result = SHGetKnownFolderPath(FOLDERID_ProgramData, 0, NULL, &temp);

    if (result != S_OK) {
        LogWriter::log("Failed to get local appdata path\n");
        return std::wstring(L"");
    }

    std::wstring path = std::wstring(temp) + APP_DIRECTORY_NAME;

    CoTaskMemFree(temp);

    return path;
}