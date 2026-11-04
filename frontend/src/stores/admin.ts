import { reactive } from 'vue'

export const adminState = reactive({
  showAdminPanel: false
})

export function setShowAdminPanel(value: boolean) {
  adminState.showAdminPanel = value
}
