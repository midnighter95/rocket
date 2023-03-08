#pragma once

#include <verilated_vpi.h>
#include <cstdlib>

inline auto vpi_get_integer(const char *name) {
  vpiHandle handle = vpi_handle_by_name((PLI_BYTE8 *) name, nullptr);
  s_vpi_value val;
  val.format = vpiIntVal;
  vpi_get_value(handle, &val);
  return val.value.integer;
}

inline uint64_t vpi_get_64(const char *name) {
  vpiHandle handle = vpi_handle_by_name((PLI_BYTE8 *) name, nullptr);
  s_vpi_value val;
  val.format = vpiHexStrVal;
  vpi_get_value(handle, &val);
  const char* p = val.value.str;
  uint64_t result = std::strtoul(p, nullptr, 16);
  return result;
}
